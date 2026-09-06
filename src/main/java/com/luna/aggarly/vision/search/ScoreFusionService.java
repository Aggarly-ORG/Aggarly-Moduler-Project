package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.ChannelSearchResult;
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.ThreeChannelResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that fuses multi-channel image and caption retrieval scores with normalized channel weighting.
 */
@Slf4j
@Component
public class ScoreFusionService {

    @Value("${aggarly.vision.search.fusion-weights.text-only.image-vector:0.15}")
    private float textModeImageWeight = 0.15f;
    @Value("${aggarly.vision.search.fusion-weights.text-only.caption-vector:0.85}")
    private float textModeCaptionWeight = 0.85f;

    @Value("${aggarly.vision.search.fusion-weights.image-only.image-vector:0.85}")
    private float imageModeImageWeight = 0.85f;
    @Value("${aggarly.vision.search.fusion-weights.image-only.caption-vector:0.15}")
    private float imageModeCaptionWeight = 0.15f;

    @Value("${aggarly.vision.search.fusion-weights.multimodal.image-vector:0.50}")
    private float multiModeImageWeight = 0.50f;
    @Value("${aggarly.vision.search.fusion-weights.multimodal.caption-vector:0.50}")
    private float multiModeCaptionWeight = 0.50f;

    public List<FusedImageCandidate> fuse(ThreeChannelResults results, SearchMode mode) {
        float wImg = switch (mode) {
            case TEXT_ONLY -> textModeImageWeight;
            case IMAGE_ONLY -> imageModeImageWeight;
            case MULTIMODAL -> multiModeImageWeight;
        };

        float wCap = switch (mode) {
            case TEXT_ONLY -> textModeCaptionWeight;
            case IMAGE_ONLY -> imageModeCaptionWeight;
            case MULTIMODAL -> multiModeCaptionWeight;
        };

        Map<UUID, ImageScoreAccumulator> imageMap = new HashMap<>();

        for (ChannelSearchResult res : results.imageChannel()) {
            ImageScoreAccumulator acc = imageMap.computeIfAbsent(res.imageId(), k -> new ImageScoreAccumulator(res.propertyId(), res.imageId(), res.payload()));
            acc.imageScore = res.score();
        }

        for (ChannelSearchResult res : results.captionChannel()) {
            ImageScoreAccumulator acc = imageMap.computeIfAbsent(res.imageId(), k -> new ImageScoreAccumulator(res.propertyId(), res.imageId(), res.payload()));
            acc.captionScore = res.score();
        }

        float totalWeight = wImg + wCap;
        List<FusedImageCandidate> fused = new ArrayList<>();

        for (ImageScoreAccumulator acc : imageMap.values()) {
            float baseScore;
            if (mode == SearchMode.TEXT_ONLY) {
                baseScore = acc.captionScore > 0 ? acc.captionScore : acc.imageScore;
            } else if (mode == SearchMode.IMAGE_ONLY) {
                if (acc.imageScore > 0 && acc.captionScore > 0) {
                    baseScore = totalWeight > 0 ? ((wImg * acc.imageScore) + (wCap * acc.captionScore)) / totalWeight : acc.imageScore;
                } else {
                    baseScore = acc.imageScore > 0 ? acc.imageScore : acc.captionScore;
                }
            } else {
                baseScore = totalWeight > 0
                        ? ((wImg * acc.imageScore) + (wCap * acc.captionScore)) / totalWeight
                        : Math.max(acc.imageScore, acc.captionScore);
            }

            String sceneType = acc.payload != null && acc.payload.containsKey("sceneType") ? acc.payload.get("sceneType").toString() : "BEDROOM";
            String qualityGrade = acc.payload != null && acc.payload.containsKey("qualityGrade") ? acc.payload.get("qualityGrade").toString() : "ACCEPTABLE";
            boolean isCover = acc.payload != null && Boolean.TRUE.equals(acc.payload.get("isCover"));
            String aiCaption = acc.payload != null && acc.payload.containsKey("aiCaption") ? acc.payload.get("aiCaption").toString() : "";

            // Quality multiplier boost (+2% for EXCELLENT, +1% for GOOD)
            float qualityBonus = switch (qualityGrade) {
                case "EXCELLENT" -> 0.02f;
                case "GOOD" -> 0.01f;
                default -> 0.0f;
            };

            // Only apply quality bonus if base score is strictly non-zero
            float fusedScore = (baseScore > 0.01f) ? Math.min(1.0f, baseScore + qualityBonus) : 0.0f;

            fused.add(new FusedImageCandidate(
                    acc.propertyId,
                    acc.imageId,
                    acc.imageScore,
                    acc.captionScore,
                    fusedScore,
                    sceneType,
                    qualityGrade,
                    isCover,
                    aiCaption,
                    acc.payload
            ));
        }

        fused.sort((a, b) -> Float.compare(b.fusedScore(), a.fusedScore()));
        return fused;
    }

    private static class ImageScoreAccumulator {
        final UUID propertyId;
        final UUID imageId;
        final Map<String, Object> payload;
        float imageScore = 0.0f;
        float captionScore = 0.0f;

        ImageScoreAccumulator(UUID propertyId, UUID imageId, Map<String, Object> payload) {
            this.propertyId = propertyId;
            this.imageId = imageId;
            this.payload = payload;
        }
    }
}
