package com.luna.aggarly.vision.search.records;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AggregatedPropertyScore(
        UUID propertyId,
        float targetSceneScore,
        float descriptionChannelScore,
        float aggregatedScore,
        List<FusedImageCandidate> matchingImages,
        UUID bestMatchImageId,
        Set<String> matchedSceneTypes,
        float roomDiversityBonus,
        float coveragePenalty,
        SceneScoreSummary sceneSummary
) {}
