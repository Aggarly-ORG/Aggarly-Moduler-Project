package com.luna.aggarly.aiagent.tool.property.record;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PropertyComparisonItem(
        UUID id,
        String title,
        String city,
        String country,
        String propertyType,
        int maxGuests,
        int bedrooms,
        int bathrooms,
        BigDecimal pricePerNight,
        BigDecimal avgRating,
        int reviewCount,
        String cancellationPolicy,
        List<String> amenities
) {}
