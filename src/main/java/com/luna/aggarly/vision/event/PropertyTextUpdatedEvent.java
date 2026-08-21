package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyTextUpdatedEvent(
        UUID propertyId,
        String title,
        String description,
        String amenitiesText,
        String houseRules,
        String locationDescription
) {}
