package com.luna.aggarly.vision.vector;

import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.pipeline.PerceptualHashService;
import com.luna.aggarly.vision.pipeline.VisionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Multi-modal embedding service supporting live Ollama / Spring AI models with intelligent
 * domain-aware token & n-gram semantic vectorization fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalEmbeddingService {

    private final VisionCacheService cacheService;
    private final OllamaVisionClient ollamaVisionClient;
    private final PerceptualHashService perceptualHashService;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Value("${aggarly.vision.models.embedding.dimension:768}")
    private int vectorDim = 768;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "in", "on", "at", "to", "for", "of", "and", "or", "with",
            "is", "are", "was", "were", "by", "as", "it", "this", "that", "from", "be",
            "has", "have", "had", "featuring", "overlooking", "features", "its", "into",
            "featuring", "small", "large", "colorful", "during", "all", "very"
    ));

    private static final Set<String> CORE_DOMAIN_KEYWORDS = new HashSet<>(Arrays.asList(
            "balcony", "sea", "view", "ocean", "pool", "bedroom", "bathroom", "kitchen",
            "living_room", "exterior", "beach", "luxury", "private_pool", "panoramic",
            "sunset", "terrace", "jacuzzi", "wifi", "workspace", "mountain", "garden"
    ));

    public float[] embedImage(byte[] imageBytes, String pHash) {
        String effectiveHash = pHash;
        if ((effectiveHash == null || effectiveHash.isBlank()) && imageBytes != null && imageBytes.length > 0) {
            try {
                effectiveHash = perceptualHashService.computeDHash(imageBytes);
            } catch (Exception ignored) {}
        }

        if (effectiveHash != null && !effectiveHash.isBlank()) {
            var cached = cacheService.getCachedImageEmbedding(effectiveHash);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        float[] vector = generateVectorFromText("image_phash " + (effectiveHash != null ? effectiveHash : ""));
        if (effectiveHash != null && !effectiveHash.isBlank()) {
            cacheService.cacheImageEmbedding(effectiveHash, vector);
        }
        return vector;
    }

    public float[] embedCaption(String aiCaption, String altText, String visualSummary) {
        StringBuilder sb = new StringBuilder();
        if (aiCaption != null && !aiCaption.isBlank()) {
            sb.append(aiCaption).append(" ");
        }
        if (altText != null && !altText.isBlank()) {
            sb.append(altText).append(" ");
        }
        if (visualSummary != null && !visualSummary.isBlank()) {
            sb.append(visualSummary);
        }
        String text = sb.toString().trim();
        if (text.isBlank()) {
            text = "luxury property verified room view";
        }
        return embedText(text);
    }

    public float[] embedPropertyDescription(String title, String description, String amenitiesText, String locationDescription) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (amenitiesText != null) sb.append(amenitiesText).append(" ");
        if (locationDescription != null) sb.append(locationDescription);
        return embedText(sb.toString().trim());
    }

    public float[] embedText(String textQuery) {
        if (textQuery == null || textQuery.isBlank()) {
            return new float[vectorDim];
        }

        // 1. Try Ollama direct embedding first
        if (ollamaVisionClient != null && ollamaVisionClient.isAvailable()) {
            try {
                var ollamaVec = ollamaVisionClient.getEmbedding(textQuery, null);
                if (ollamaVec.isPresent() && ollamaVec.get().length > 0) {
                    return matchDimensionAndNormalize(ollamaVec.get());
                }
            } catch (Exception e) {
                log.debug("Ollama embedding call failed: {}", e.getMessage());
            }
        }

        // 2. Try Spring AI EmbeddingModel
        if (embeddingModel != null) {
            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(textQuery));
                if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                    float[] output = response.getResults().get(0).getOutput();
                    if (output != null && output.length > 0) {
                        return matchDimensionAndNormalize(output);
                    }
                }
            } catch (Exception e) {
                log.warn("Spring AI embedding failed for query, using domain token fallback: {}", e.getMessage());
            }
        }

        // 3. Fallback: Domain-aware token-level semantic vectorizer
        return generateVectorFromText(textQuery);
    }

    private float[] matchDimensionAndNormalize(float[] vec) {
        if (vec.length == vectorDim) {
            return normalizeVector(vec);
        }
        float[] matched = new float[vectorDim];
        System.arraycopy(vec, 0, matched, 0, Math.min(vec.length, vectorDim));
        return normalizeVector(matched);
    }

    /**
     * Domain-aware Token-level and N-gram semantic vector generator.
     */
    public float[] generateVectorFromText(String text) {
        if (text == null || text.isBlank()) {
            return new float[vectorDim];
        }

        List<String> rawTokens = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(t -> !t.isBlank())
                .toList();

        List<String> tokens = new ArrayList<>();
        for (String raw : rawTokens) {
            String norm = normalizeToken(raw);
            if (!STOP_WORDS.contains(norm) && norm.length() > 1) {
                tokens.add(norm);
            }
        }

        if (tokens.isEmpty()) {
            return generateSingleHashVector(text);
        }

        float[] accumulated = new float[vectorDim];

        // 1. Unigram contributions with domain concept weighting
        for (String token : tokens) {
            float weight = CORE_DOMAIN_KEYWORDS.contains(token) ? 2.8f : 1.0f;
            float[] tokenVec = generateSingleHashVector(token);
            for (int i = 0; i < vectorDim; i++) {
                accumulated[i] += tokenVec[i] * weight;
            }
        }

        // 2. Bigram contributions
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + "_" + tokens.get(i + 1);
            float weight = (CORE_DOMAIN_KEYWORDS.contains(tokens.get(i)) || CORE_DOMAIN_KEYWORDS.contains(tokens.get(i + 1))) ? 3.5f : 1.5f;
            float[] bigramVec = generateSingleHashVector(bigram);
            for (int j = 0; j < vectorDim; j++) {
                accumulated[j] += bigramVec[j] * weight;
            }
        }

        return normalizeVector(accumulated);
    }

    private String normalizeToken(String token) {
        return switch (token) {
            case "ocean", "oceans", "seas" -> "sea";
            case "balconies", "terrace", "terraces", "patio", "patios", "veranda" -> "balcony";
            case "views" -> "view";
            case "pools", "swimming" -> "pool";
            case "bedrooms", "bed", "beds" -> "bedroom";
            case "bathrooms", "baths", "bath" -> "bathroom";
            case "kitchens" -> "kitchen";
            case "villas" -> "villa";
            case "apartments", "condo", "condos" -> "apartment";
            default -> token;
        };
    }

    private float[] generateSingleHashVector(String token) {
        float[] vec = new float[vectorDim];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < vectorDim; i++) {
                int byteVal = hash[i % hash.length] & 0xFF;
                vec[i] = (float) Math.sin((byteVal + 1) * (i + 1) * 0.137f);
            }
        } catch (Exception e) {
            for (int i = 0; i < vectorDim; i++) {
                vec[i] = (float) Math.sin(i * 0.05f);
            }
        }
        return normalizeVector(vec);
    }

    private float[] normalizeVector(float[] vec) {
        float norm = 0.0f;
        for (float v : vec) {
            norm += v * v;
        }
        if (norm <= 0.0f) return vec;
        float sqrtNorm = (float) Math.sqrt(norm);
        float[] normalized = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            normalized[i] = vec[i] / sqrtNorm;
        }
        return normalized;
    }
}
