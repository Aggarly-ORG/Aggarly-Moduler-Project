package com.luna.aggarly.vision.pipeline.records;

import java.util.List;
import java.util.Map;

public record VisionInferenceResult(
        String sceneType,
        double sceneConfidence,
        Boolean isIndoor,
        String viewType,
        String aiCaption,
        String altText,
        String ocrText,
        List<DetectedObject> detectedObjects,
        List<DetectedAmenity> detectedAmenities,
        List<StyleTag> styleTags,
        List<String> dominantColors,
        Map<String, Object> conditionAssessment,
        List<String> specialFeatures,
        String moderationStatus,
        String moderationReason,
        String classificationModelName,
        String classificationModelVersion,
        String promptVersion,
        String preprocessingVersion
) {}
