package com.luna.aggarly.vision.dto;

import lombok.Builder;
import java.util.List;
import java.util.UUID;

/**
 * Perception output for a single fast photo analysis turn.
 */
@Builder
public record FastVisionAnalysisResponse(
        UUID imageId,
        String imageKey,
        String imageUrl,
        String sceneType,
        double sceneConfidence,
        Boolean isIndoor,
        String viewType,
        String aiCaption,
        List<String> detectedAmenities,
        List<UUID> detectedAmenityIds,
        List<String> styleTags,
        List<String> dominantColors,
        String architecturalSummary,
        List<UiCommandDto> uiCommands
) {}
