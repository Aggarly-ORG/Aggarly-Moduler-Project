package com.luna.aggarly.vision.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-modal visual reranker providing Stage 2 LLM verification when an LLM provider is active,
 * or immediate high-precision deterministic explanation when offline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModalVisualReranker {

    private final PropertyRepository propertyRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final OllamaVisionClient ollamaVisionClient;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    @Value("${aggarly.vision.models.reranking.llm-judge-enabled:true}")
    private boolean llmRerankerEnabled = true;

    public record RerankedItem(
            AggregatedPropertyScore propertyScore,
            float finalScore,
            String explanation
    ) {}

    public List<RerankedItem> rerank(List<AggregatedPropertyScore> candidates, VisionSearchQuery query) {
        List<RerankedItem> results = new ArrayList<>();
        boolean llmReachable = llmRerankerEnabled && (ollamaVisionClient != null && ollamaVisionClient.isAvailable());

        for (AggregatedPropertyScore cand : candidates) {
            Property prop = propertyRepository.findById(cand.propertyId()).orElse(null);
            if (prop == null) continue;

            PropertyVisualProfile profile = profileRepository.findByPropertyId(cand.propertyId()).orElse(null);

            String explanation = buildDeterministicExplanation(cand, profile, query.rawText());
            float finalScore = cand.aggregatedScore();

            if (llmReachable && chatClientBuilder != null && query.rawText() != null && !query.rawText().isBlank()) {
                try {
                    var llmJudgement = evaluateWithLlm(cand, prop, profile, query.rawText());
                    if (llmJudgement != null) {
                        finalScore = (cand.aggregatedScore() * 0.70f) + (llmJudgement.score * 0.30f);
                        explanation = llmJudgement.explanation;
                    }
                } catch (Exception e) {
                    log.debug("LLM reranking skipped for property {}: {}", cand.propertyId(), e.getMessage());
                }
            }

            results.add(new RerankedItem(cand, finalScore, explanation));
        }

        results.sort((a, b) -> Float.compare(b.finalScore(), a.finalScore()));
        return results;
    }

    private record LlmJudgement(float score, String explanation) {}

    private LlmJudgement evaluateWithLlm(AggregatedPropertyScore cand, Property prop, PropertyVisualProfile profile, String userQuery) {
        StringBuilder visualEvidence = new StringBuilder();
        int count = 0;
        for (FusedImageCandidate img : cand.matchingImages()) {
            if (count++ >= 3) break;
            visualEvidence.append(String.format("- Image (%s): %s\n", img.sceneType(), img.aiCaption()));
        }

        String prompt = String.format("""
                You are a property visual relevance judge for Aggarly.
                The guest is searching for: "%s"

                Property: %s (%s, %s)
                VISUAL EVIDENCE:
                %s
                LISTING DESCRIPTION EXCERPT:
                %s
                OVERALL STYLES: %s

                Rate the visual match from 0.0 to 1.0 and write a concise 1-sentence factual explanation.
                Respond with JSON ONLY: {"score": 0.95, "explanation": "Matches your modern sea view aesthetic with panoramic bedroom windows and verified private pool."}
                """,
                userQuery,
                prop.getTitle(),
                prop.getAddress() != null ? prop.getAddress().getCity() : "",
                prop.getAddress() != null ? prop.getAddress().getCountry() : "",
                visualEvidence,
                prop.getDescription() != null ? prop.getDescription().substring(0, Math.min(200, prop.getDescription().length())) : "",
                profile != null ? profile.getAggregatedStyleTagsJson() : ""
        );

        ChatClient chatClient = chatClientBuilder.build();
        String response = chatClient.prompt().system(prompt).call().content();

        if (response != null) {
            int first = response.indexOf('{');
            int last = response.lastIndexOf('}');
            if (first >= 0 && last > first) {
                try {
                    JsonNode node = objectMapper.readTree(response.substring(first, last + 1));
                    float score = (float) node.path("score").asDouble(cand.aggregatedScore());
                    String expl = node.path("explanation").asText("Visually matched property.");
                    return new LlmJudgement(score, expl);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String buildDeterministicExplanation(AggregatedPropertyScore cand, PropertyVisualProfile profile, String userQuery) {
        if (cand.matchedSceneTypes() != null && !cand.matchedSceneTypes().isEmpty()) {
            return String.format("Visually matches your search with verified %s views and contemporary aesthetic.",
                    String.join(", ", cand.matchedSceneTypes())
            );
        }
        return "Strong visual and listing relevance match for your inquiry.";
    }
}
