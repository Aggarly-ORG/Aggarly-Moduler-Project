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
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Cross-modal visual reranker providing Stage 2 LLM verification on top candidates
 * via direct Ollama client, or high-precision deterministic explanation when offline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModalVisualReranker {

    private final PropertyRepository propertyRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final OllamaVisionClient ollamaVisionClient;
    private final ObjectMapper objectMapper;

    @Value("${aggarly.vision.models.reranking.llm-judge-enabled:true}")
    private boolean llmRerankerEnabled = true;

    @Value("${aggarly.vision.models.reranking.max-candidates-to-judge:3}")
    private int maxCandidatesToJudge = 3;

    public record RerankedItem(
            AggregatedPropertyScore propertyScore,
            float finalScore,
            String explanation
    ) {}

    public List<RerankedItem> rerank(List<AggregatedPropertyScore> candidates, VisionSearchQuery query) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<RerankedItem> results = new ArrayList<>();
        boolean llmReachable = llmRerankerEnabled && (ollamaVisionClient != null && ollamaVisionClient.isAvailable());

        int count = 0;
        for (AggregatedPropertyScore cand : candidates) {
            Property prop = propertyRepository.findById(cand.propertyId()).orElse(null);
            if (prop == null) continue;

            PropertyVisualProfile profile = profileRepository.findByPropertyId(cand.propertyId()).orElse(null);

            String explanation = buildDeterministicExplanation(cand, profile, query.rawText());
            float finalScore = cand.aggregatedScore();

            // LLM verification evaluated strictly on top candidates to balance quality with SLA
            if (llmReachable && count < maxCandidatesToJudge) {
                try {
                    LlmJudgement judgement = evaluateWithLlm(cand, prop, profile, query);
                    if (judgement != null) {
                        finalScore = (cand.aggregatedScore() * 0.70f) + (judgement.score * 0.30f);
                        if (judgement.explanation != null && !judgement.explanation.isBlank()) {
                            explanation = judgement.explanation;
                        }
                    }
                } catch (Exception e) {
                    log.debug("LLM reranking skipped for property {}: {}", cand.propertyId(), e.getMessage());
                }
            }

            results.add(new RerankedItem(cand, finalScore, explanation));
            count++;
        }

        results.sort((a, b) -> Float.compare(b.finalScore(), a.finalScore()));
        return results;
    }

    private record LlmJudgement(float score, String explanation) {}

    private LlmJudgement evaluateWithLlm(AggregatedPropertyScore cand, Property prop, PropertyVisualProfile profile, VisionSearchQuery query) {
        StringBuilder visualEvidence = new StringBuilder();
        int count = 0;
        for (FusedImageCandidate img : cand.matchingImages()) {
            if (count++ >= 3) break;
            visualEvidence.append(String.format("- Image (%s): %s\n", img.sceneType(), img.aiCaption()));
        }

        String base64Image = null;
        if (query.referenceImageBytes() != null && query.referenceImageBytes().length > 0) {
            base64Image = Base64.getEncoder().encodeToString(query.referenceImageBytes());
        }

        String systemPrompt = "You are a real-estate property visual relevance evaluator for Aggarly. "
                + "Rate the visual match from 0.0 to 1.0 and write a concise 1-sentence factual explanation. "
                + "Respond with JSON ONLY: {\"score\": 0.95, \"explanation\": \"Matches the cliffside infinity pool aesthetic.\"}";

        String userPrompt;
        if (query.searchMode() == SearchMode.IMAGE_ONLY) {
            userPrompt = String.format("""
                    Reference Photo: [Provided Image]
                    Candidate Property: %s (%s, %s)
                    VISUAL EVIDENCE FROM LISTING:
                    %s
                    OVERALL STYLES: %s
                    DESCRIPTION: %s

                    Rate how well this property matches the visual aesthetic and architecture in the reference photo from 0.0 to 1.0.
                    Respond with JSON ONLY: {"score": 0.95, "explanation": "Matches the Mediterranean cliffside aesthetic with private terrace."}
                    """,
                    prop.getTitle(),
                    prop.getAddress() != null ? prop.getAddress().getCity() : "",
                    prop.getAddress() != null ? prop.getAddress().getCountry() : "",
                    visualEvidence,
                    profile != null ? profile.getAggregatedStyleTagsJson() : "",
                    prop.getDescription() != null ? prop.getDescription().substring(0, Math.min(200, prop.getDescription().length())) : ""
            );
        } else {
            userPrompt = String.format("""
                    The guest is searching for: "%s"
                    Candidate Property: %s (%s, %s)
                    VISUAL EVIDENCE FROM LISTING:
                    %s
                    OVERALL STYLES: %s
                    DESCRIPTION: %s

                    Rate the visual match from 0.0 to 1.0.
                    Respond with JSON ONLY: {"score": 0.95, "explanation": "Matches your modern sea view aesthetic with panoramic bedroom windows and verified private pool."}
                    """,
                    query.rawText(),
                    prop.getTitle(),
                    prop.getAddress() != null ? prop.getAddress().getCity() : "",
                    prop.getAddress() != null ? prop.getAddress().getCountry() : "",
                    visualEvidence,
                    profile != null ? profile.getAggregatedStyleTagsJson() : "",
                    prop.getDescription() != null ? prop.getDescription().substring(0, Math.min(200, prop.getDescription().length())) : ""
            );
        }

        Optional<String> responseOpt = ollamaVisionClient.chatWithVision(base64Image, systemPrompt, userPrompt, null);
        if (responseOpt.isPresent()) {
            String response = responseOpt.get();
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
