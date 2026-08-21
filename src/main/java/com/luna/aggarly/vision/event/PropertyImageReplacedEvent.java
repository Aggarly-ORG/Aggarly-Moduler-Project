package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyImageReplacedEvent(
        UUID imageId,
        UUID propertyId,
        String newObjectKey
) {}
