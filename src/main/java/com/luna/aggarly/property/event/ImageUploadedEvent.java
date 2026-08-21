package com.luna.aggarly.property.event;

import java.util.UUID;

public record ImageUploadedEvent(
        UUID propertyId,
        UUID propertyImageId
) {
}