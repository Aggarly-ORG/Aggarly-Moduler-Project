package com.luna.aggarly.vision.search.records;

import java.util.Map;
import java.util.UUID;

public record FusedImageCandidate(
        UUID propertyId,
        UUID imageId,
        float imageVectorScore,
        float captionVectorScore,
        float fusedScore,
        String sceneType,
        String qualityGrade,
        boolean isCover,
        String aiCaption,
        Map<String, Object> payload
) {}
