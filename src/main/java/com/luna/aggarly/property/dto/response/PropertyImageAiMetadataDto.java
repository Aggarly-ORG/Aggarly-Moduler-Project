package com.luna.aggarly.property.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PropertyImageAiMetadataDto(
        UUID id,
        UUID propertyImageId,
        String processingStatus,
        String hostCaption,
        String aiCaption,
        String altText,
        String ocrText,
        String detectedObjectsJson,
        String styleTagsJson,
        UUID qdrantPointId,
        String qdrantCollection,
        String embeddingModel,
        Instant embeddedAt,
        String moderationStatus,
        String moderationReason
) {
}
