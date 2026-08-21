package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyImageDeletedEvent(
        UUID imageId,
        UUID propertyId
) {}
