package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.ChannelSearchResult;
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.SceneScoreSummary;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Category-aware property score aggregator that dynamically normalizes active channel weights,
 * prevents channel dilution, and applies room diversity bonuses.
 */
@Slf4j
@Component
public class PropertyScoreAggregator {

    @Value("${aggarly.vision.search.property-score-weights.target-scene:0.65}")
    private float targetSceneWeight = 0.65f;

    @Value("${aggarly.vision.search.property-score-weights.description:0.20}")
    private float descriptionWeight = 0.20f;

    @Value("${aggarly.vision.search.property-score-weights.secondary-avg:0.15}")
    private float secondaryAvgWeight = 0.15f;

    @Value("${aggarly.vision.search.property-score-weights.room-bonus-max:0.10}")
    private float roomBonusMax = 0.10f;

    public List<AggregatedPropertyScore> aggregate(
            List<FusedImageCandidate> fusedImages,
            List<ChannelSearchResult> descriptionResults,
            VisionSearchFilters filters) {

        // 1. Group images by property
        Map<UUID, List<FusedImageCandidate>> propertyImagesMap = new HashMap<>();
        for (FusedImageCandidate img : fusedImages) {
            propertyImagesMap.computeIfAbsent(img.propertyId(), k -> new ArrayList<>()).add(img);
        }

        // 2. Map description scores by property
        Map<UUID, Float> descScoreMap = new HashMap<>();
        if (descriptionResults != null) {
            for (ChannelSearchResult res : descriptionResults) {
                descScoreMap.put(res.propertyId(), Math.max(descScoreMap.getOrDefault(res.propertyId(), 0.0f), res.score()));
            }
        }

        // 3. Aggregate each property
        Set<UUID> allPropertyIds = new HashSet<>(propertyImagesMap.keySet());
        allPropertyIds.addAll(descScoreMap.keySet());

        List<AggregatedPropertyScore> scores = new ArrayList<>();

        for (UUID propId : allPropertyIds) {
            List<FusedImageCandidate> propImages = propertyImagesMap.getOrDefault(propId, List.of());
            float descScore = descScoreMap.getOrDefault(propId, 0.0f);

            SceneScoreSummary sceneSummary = computeSceneSummary(propImages);

            float bestOverallScore = 0.0f;
            float secondarySum = 0.0f;
            UUID bestImageId = null;
            Set<String> matchedScenes = new HashSet<>();

            if (!propImages.isEmpty()) {
                bestOverallScore = propImages.get(0).fusedScore();
                bestImageId = propImages.get(0).imageId();

                for (int i = 0; i < propImages.size(); i++) {
                    FusedImageCandidate img = propImages.get(i);
                    matchedScenes.add(img.sceneType());
                    if (i > 0) {
                        secondarySum += img.fusedScore();
                    }
                }
            }

            float secondaryAvg = propImages.size() > 1 ? secondarySum / (propImages.size() - 1) : 0.0f;

            // Target scene score resolution
            float targetSceneScore = bestOverallScore;
            if (filters != null && filters.requiredSceneTypes() != null && !filters.requiredSceneTypes().isEmpty()) {
                float targetSum = 0.0f;
                int count = 0;
                for (String reqScene : filters.requiredSceneTypes()) {
                    float sceneScore = getSceneScore(sceneSummary, reqScene);
                    if (sceneScore > 0) {
                        targetSum += sceneScore;
                        count++;
                    }
                }
                if (count > 0) {
                    targetSceneScore = (targetSum / count);
                }
            }

            // Dynamic Active-Weight Normalization:
            // Prevents uninvoked channels (like empty description scores) from artificially crushing visual relevance
            float activeWeight = targetSceneScore > 0 ? targetSceneWeight : 0.0f;
            float weightedSum = targetSceneScore > 0 ? (targetSceneWeight * targetSceneScore) : 0.0f;

            if (descScore > 0.0f) {
                weightedSum += descriptionWeight * descScore;
                activeWeight += descriptionWeight;
            }

            if (secondaryAvg > 0.0f && propImages.size() > 1) {
                weightedSum += secondaryAvgWeight * secondaryAvg;
                activeWeight += secondaryAvgWeight;
            }

            float baseScore = activeWeight > 0 ? (weightedSum / activeWeight) : targetSceneScore;

            // Room diversity bonus (+0.03 per distinct room scene, capped at roomBonusMax)
            float roomBonus = Math.min(roomBonusMax, matchedScenes.size() * 0.03f);

            // Final Composite Aggregated Score
            float aggregatedScore = Math.min(1.0f, baseScore + roomBonus);

            scores.add(new AggregatedPropertyScore(
                    propId,
                    targetSceneScore,
                    descScore,
                    aggregatedScore,
                    propImages,
                    bestImageId,
                    matchedScenes,
                    roomBonus,
                    0.0f,
                    sceneSummary
            ));
        }

        scores.sort((a, b) -> Float.compare(b.aggregatedScore(), a.aggregatedScore()));
        return scores;
    }

    private SceneScoreSummary computeSceneSummary(List<FusedImageCandidate> images) {
        float bestBedroom = 0.0f, bestBathroom = 0.0f, bestLiving = 0.0f;
        float bestExterior = 0.0f, bestPool = 0.0f, bestView = 0.0f;
        Map<String, UUID> bestMap = new HashMap<>();

        for (FusedImageCandidate img : images) {
            String st = img.sceneType().toUpperCase();
            float score = img.fusedScore();
            switch (st) {
                case "BEDROOM" -> {
                    if (score > bestBedroom) { bestBedroom = score; bestMap.put(st, img.imageId()); }
                }
                case "BATHROOM" -> {
                    if (score > bestBathroom) { bestBathroom = score; bestMap.put(st, img.imageId()); }
                }
                case "LIVING_ROOM", "DINING" -> {
                    if (score > bestLiving) { bestLiving = score; bestMap.put(st, img.imageId()); }
                }
                case "EXTERIOR" -> {
                    if (score > bestExterior) { bestExterior = score; bestMap.put(st, img.imageId()); }
                }
                case "POOL" -> {
                    if (score > bestPool) { bestPool = score; bestMap.put(st, img.imageId()); }
                }
                case "VIEW", "BALCONY" -> {
                    if (score > bestView) { bestView = score; bestMap.put(st, img.imageId()); }
                }
            }
        }

        return new SceneScoreSummary(
                bestBedroom, bestBathroom, bestLiving, bestExterior, bestPool, bestView, bestMap
        );
    }

    private float getSceneScore(SceneScoreSummary summary, String sceneType) {
        return switch (sceneType.toUpperCase()) {
            case "BEDROOM" -> summary.bestBedroomScore();
            case "BATHROOM" -> summary.bestBathroomScore();
            case "LIVING_ROOM" -> summary.bestLivingRoomScore();
            case "EXTERIOR" -> summary.bestExteriorScore();
            case "POOL" -> summary.bestPoolScore();
            case "VIEW", "BALCONY" -> summary.bestViewScore();
            default -> 0.0f;
        };
    }
}
