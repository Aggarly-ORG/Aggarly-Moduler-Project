package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyCreatedEvent(
        UUID propertyId,
        String title,
        String description,
        String city,
        String country
) {}
