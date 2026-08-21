package com.luna.aggarly.aiagent.tool.property.record;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PropertyDetailsResponse(
        UUID id,
        String title,
        String description,
        String propertyType,
        int maxGuests,
        int bedrooms,
        int bathrooms,
        BigDecimal basePricePerNight,
        String cancellationPolicy,
        String status,
        BigDecimal avgRating,
        int reviewCount,
        String streetAddress,
        String city,
        String state,
        String country,
        String postalCode,
        Double latitude,
        Double longitude,
        List<String> amenities,
        List<String> imageUrls,
        String coverImageUrl,
        String imageUrl,
        List<String> images,
        UUID hostId
) {
    public PropertyDetailsResponse(
            UUID id,
            String title,
            String description,
            String propertyType,
            int maxGuests,
            int bedrooms,
            int bathrooms,
            BigDecimal basePricePerNight,
            String cancellationPolicy,
            String status,
            BigDecimal avgRating,
            int reviewCount,
            String streetAddress,
            String city,
            String state,
            String country,
            String postalCode,
            Double latitude,
            Double longitude,
            List<String> amenities,
            List<String> imageUrls,
            UUID hostId
    ) {
        this(
                id,
                title,
                description,
                propertyType,
                maxGuests,
                bedrooms,
                bathrooms,
                basePricePerNight,
                cancellationPolicy,
                status,
                avgRating,
                reviewCount,
                streetAddress,
                city,
                state,
                country,
                postalCode,
                latitude,
                longitude,
                amenities,
                imageUrls,
                (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null,
                (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null,
                imageUrls != null ? imageUrls : List.of(),
                hostId
        );
    }
}
