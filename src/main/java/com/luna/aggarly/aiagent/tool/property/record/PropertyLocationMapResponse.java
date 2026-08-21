package com.luna.aggarly.aiagent.tool.property.record;

import java.util.UUID;

public record PropertyLocationMapResponse(
        UUID propertyId,
        String streetAddress,
        String city,
        String state,
        String country,
        String postalCode,
        Double latitude,
        Double longitude,
        String mapCoordinatesUrl
) {}
