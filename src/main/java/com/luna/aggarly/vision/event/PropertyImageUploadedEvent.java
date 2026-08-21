package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyImageUploadedEvent(
        UUID imageId,
        UUID propertyId,
        String objectKey,
        boolean isCover
) {}
