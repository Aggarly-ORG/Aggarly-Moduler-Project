package com.luna.aggarly.property.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SavePropertyImageAiMetadataRequest(
        @NotNull(message = "Property image ID is required")
        UUID propertyImageId,
        String hostCaption,
        String aiCaption,
        String altText,
        String ocrText,
        String detectedObjectsJson,
        String styleTagsJson,
        String embeddingModel
) {
}
