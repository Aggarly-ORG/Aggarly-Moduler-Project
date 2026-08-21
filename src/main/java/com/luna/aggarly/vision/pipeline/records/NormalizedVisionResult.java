package com.luna.aggarly.vision.pipeline.records;

import java.util.List;
import java.util.Map;

public record NormalizedVisionResult(
        VisionInferenceResult raw,
        List<StyleTag> normalizedStyleTags,
        List<DetectedAmenity> normalizedAmenities,
        List<DetectedObject> filteredObjects,
        Map<String, Double> visualFitScores,
        String roomClusterId
) {}
