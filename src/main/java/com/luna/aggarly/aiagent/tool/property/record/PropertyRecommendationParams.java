package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

public record PropertyRecommendationParams(
        @JsonPropertyDescription("Preferred destination or city for the trip (e.g. 'Paris', 'Rome').")
        String destination,

        @JsonPropertyDescription("Number of guests requiring accommodation.")
        Integer guests,

        @JsonPropertyDescription("Maximum nightly price budget.")
        BigDecimal maxPrice,

        @JsonPropertyDescription("Preferred amenities (e.g. ['Pool', 'Wi-Fi', 'Kitchen']).")
        List<String> preferredAmenities
) {}
