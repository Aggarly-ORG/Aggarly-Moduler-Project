package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;
import java.util.UUID;

public record PropertyAmenitiesResponse(
        UUID propertyId,
        int totalAmenities,
        List<AmenityItem> amenities
) {}
