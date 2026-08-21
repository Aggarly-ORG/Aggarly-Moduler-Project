package com.luna.aggarly.aiagent.tool.property.record;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PropertySummaryItem(
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
        String coverImageUrl,
        String imageUrl,
        List<String> images
) {
    public PropertySummaryItem(
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
            String coverImageUrl
    ) {
        this(
                id,
                title,
                city,
                country,
                propertyType,
                maxGuests,
                bedrooms,
                bathrooms,
                pricePerNight,
                avgRating,
                reviewCount,
                coverImageUrl,
                coverImageUrl,
                coverImageUrl != null ? List.of(coverImageUrl) : List.of()
        );
    }
}
