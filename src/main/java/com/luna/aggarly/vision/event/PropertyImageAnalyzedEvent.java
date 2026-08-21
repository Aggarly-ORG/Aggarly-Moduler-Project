package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyImageAnalyzedEvent(
        UUID imageId,
        UUID propertyId,
        String objectKey,
        boolean requiresEmbedding
) {}
