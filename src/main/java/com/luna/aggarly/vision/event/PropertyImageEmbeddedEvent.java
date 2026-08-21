package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyImageEmbeddedEvent(
        UUID imageId,
        UUID propertyId,
        UUID qdrantPointId
) {}
