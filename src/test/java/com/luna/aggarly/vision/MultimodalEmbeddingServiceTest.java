package com.luna.aggarly.vision;

import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.pipeline.PerceptualHashService;
import com.luna.aggarly.vision.pipeline.VisionCacheService;
import com.luna.aggarly.vision.search.PropertyScoreAggregator;
import com.luna.aggarly.vision.search.ScoreFusionService;
import com.luna.aggarly.vision.search.VisionContextualFilter;
import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.ChannelSearchResult;
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.ThreeChannelResults;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.vector.MultimodalEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultimodalEmbeddingServiceTest {

    private MultimodalEmbeddingService embeddingService;
    private ScoreFusionService scoreFusionService;
    private PropertyScoreAggregator scoreAggregator;
    private VisionContextualFilter contextualFilter;

    @BeforeEach
    void setUp() {
        VisionCacheService cacheService = Mockito.mock(VisionCacheService.class);
        OllamaVisionClient ollamaClient = Mockito.mock(OllamaVisionClient.class);
        PerceptualHashService pHashService = Mockito.mock(PerceptualHashService.class);
        com.luna.aggarly.vision.client.ClipServiceClient clipClient = Mockito.mock(com.luna.aggarly.vision.client.ClipServiceClient.class);

        embeddingService = new MultimodalEmbeddingService(cacheService, ollamaClient, pHashService, clipClient);
        scoreFusionService = new ScoreFusionService();
        scoreAggregator = new PropertyScoreAggregator();
        contextualFilter = new VisionContextualFilter();
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0.0f, normA = 0.0f, normB = 0.0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    @Test
    @DisplayName("Verify perceptual hash projection gives 1.0 for same image and ~0.0 for shoe vs pool")
    void testPerceptualHashOrthogonalProjection() {
        String poolHash = "ff80c0e070381c0e";
        String samePoolHash = "ff80c0e070381c0e";
        String shoeHash = "007f3f1f8fc7e3f1";

        float[] poolVec = embeddingService.projectPerceptualHashToVector(poolHash);
        float[] samePoolVec = embeddingService.projectPerceptualHashToVector(samePoolHash);
        float[] shoeVec = embeddingService.projectPerceptualHashToVector(shoeHash);

        float sameSimilarity = cosineSimilarity(poolVec, samePoolVec);
        float shoeVsPoolSimilarity = cosineSimilarity(shoeVec, poolVec);

        System.out.println("Same Image Similarity: " + sameSimilarity);
        System.out.println("Shoe vs Pool Similarity: " + shoeVsPoolSimilarity);

        assertEquals(1.0f, sameSimilarity, 0.001f, "Identical image must have 1.00 cosine similarity");
        assertTrue(shoeVsPoolSimilarity < 0.15f, "Unrelated shoe vs pool image must have near-zero (< 0.15) similarity, was: " + shoeVsPoolSimilarity);
    }

    @Test
    @DisplayName("Verify semantic token vectorizer produces strong relevance for Balcony Sea View query")
    void testBalconySeaViewSemanticSimilarity() {
        String query = "Balcony sea view";
        String aiCaption = "A cozy outdoor balcony featuring wicker lounge chairs, a turquoise cushioned sofa, and a small table with a colorful patterned cloth, overlooking a panoramic ocean view at sunset.";
        String altText = "Balcony with wicker furniture and a sea view.";
        String visualSummary = "A cozy outdoor balcony featuring wicker lounge chairs, a turquoise cushioned sofa, overlooking a panoramic ocean view at sunset. Detected features: [balcony, sea_view, outdoor_seating, panoramic_view].";

        float[] queryVec = embeddingService.embedText(query);
        float[] captionVec = embeddingService.embedCaption(aiCaption, altText, visualSummary);

        float similarity = cosineSimilarity(queryVec, captionVec);
        System.out.println("Computed Cosine Similarity: " + similarity);

        assertTrue(similarity >= 0.50f, "Cosine similarity should be strong (>= 0.50) for matching balcony sea view concepts, was: " + similarity);

        UUID propertyId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "propertyId", propertyId.toString(),
                "imageId", imageId.toString(),
                "sceneType", "BALCONY",
                "viewType", "SEA_VIEW",
                "qualityGrade", "EXCELLENT",
                "qualityScore", 0.93,
                "aiCaption", aiCaption
        );

        ThreeChannelResults results = new ThreeChannelResults(
                List.of(new ChannelSearchResult(imageId, propertyId, 0.40f, "image_vector", payload)),
                List.of(new ChannelSearchResult(imageId, propertyId, similarity, "caption_vector", payload)),
                List.of()
        );

        List<FusedImageCandidate> fused = scoreFusionService.fuse(results, SearchMode.TEXT_ONLY);
        assertFalse(fused.isEmpty());

        FusedImageCandidate best = fused.get(0);
        System.out.println("Fused Score: " + best.fusedScore() + ", caption score: " + best.captionVectorScore());

        assertTrue(best.fusedScore() >= 0.50f, "Fused score should be >= 0.50, was: " + best.fusedScore());

        // Test Property Score Aggregation & Contextual Filter Passing
        List<AggregatedPropertyScore> aggregatedScores = scoreAggregator.aggregate(fused, List.of(), null);
        assertFalse(aggregatedScores.isEmpty());

        AggregatedPropertyScore propScore = aggregatedScores.get(0);
        System.out.println("Aggregated Property Score: " + propScore.aggregatedScore());
        assertTrue(propScore.aggregatedScore() >= 0.50f, "Aggregated score should be >= 0.50, was: " + propScore.aggregatedScore());

        VisionSearchQuery searchQuery = VisionSearchQuery.textQuery(query, null, 10, null, 0.20f);
        List<AggregatedPropertyScore> filtered = contextualFilter.filter(aggregatedScores, searchQuery);
        assertFalse(filtered.isEmpty(), "Property MUST pass the contextual filter threshold");
    }
}
