package com.luna.aggarly.vision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload for fast sequential photo analysis in Host Wizard / Management.
 */
public record FastVisionAnalysisRequest(
        UUID propertyId,
        String draftId,
        @NotNull UUID conversationId,
        @NotBlank String imageKey,
        String imageUrl,
        String fileName,
        Integer displayOrder
) {}
