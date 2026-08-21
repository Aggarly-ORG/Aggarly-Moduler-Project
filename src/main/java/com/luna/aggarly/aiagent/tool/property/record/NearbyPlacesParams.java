package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NearbyPlacesParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier (UUID) of the property.")
        UUID propertyId,

        @JsonPropertyDescription("Optional category of places to search: 'all', 'restaurants', 'transit', 'attractions', 'groceries'.")
        String category
) {}
