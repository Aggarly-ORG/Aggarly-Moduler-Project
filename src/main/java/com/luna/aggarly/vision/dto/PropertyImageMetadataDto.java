package com.luna.aggarly.vision.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PropertyImageMetadataDto(
        UUID imageId,
        UUID propertyId,
        String processingStatus,
        String currentStage,
        String sceneType,
        Double sceneConfidence,
        Boolean isIndoor,
        String viewType,
        String aiCaption,
        String altText,
        String qualityGrade,
        Double qualityScore,
        Double technicalQualityScore,
        Double visualUsabilityScore,
        Double searchabilityScore,
        String perceptualHash,
        String duplicateClassification,
        List<Object> detectedObjects,
        List<Object> detectedAmenities,
        List<Object> styleTags,
        List<String> dominantColors,
        Map<String, Object> conditionAssessment,
        String visualSummary,
        String moderationStatus
) {}
