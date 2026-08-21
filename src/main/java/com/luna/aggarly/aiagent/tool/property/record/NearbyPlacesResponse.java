package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;
import java.util.UUID;

public record NearbyPlacesResponse(
        UUID propertyId,
        String category,
        List<NearbyPlaceItem> places
) {}
