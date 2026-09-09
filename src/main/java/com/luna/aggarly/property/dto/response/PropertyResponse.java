package com.luna.aggarly.property.dto.response;

import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.entity.enums.PropertyType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        UUID hostId,
        String title,
        String description,
        PropertyType propertyType,
        int maxGuests,
        int bedrooms,
        int bathrooms,
        BigDecimal basePricePerNight,
        CancellationPolicy cancellationPolicy,
        BigDecimal latitude,
        BigDecimal longitude,
        PropertyStatus status,
        BigDecimal avgRating,
        int reviewCount,
        AddressResponse address,
        List<PropertyImageResponse> images,
        Set<AmenityResponse> amenities,
        Integer bortleClass,
        BigDecimal acousticAmbientDb,
        BigDecimal astrophotographyScore,
        Boolean featuredSpotlight,
        String revisionNotes
) {
    public PropertyResponse(
            UUID id, UUID hostId, String title, String description,
            PropertyType propertyType, int maxGuests, int bedrooms, int bathrooms,
            BigDecimal basePricePerNight, CancellationPolicy cancellationPolicy,
            BigDecimal latitude, BigDecimal longitude, PropertyStatus status,
            BigDecimal avgRating, int reviewCount, AddressResponse address,
            List<PropertyImageResponse> images, Set<AmenityResponse> amenities
    ) {
        this(id, hostId, title, description, propertyType, maxGuests, bedrooms, bathrooms,
             basePricePerNight, cancellationPolicy, latitude, longitude, status,
             avgRating, reviewCount, address, images, amenities,
             3, new BigDecimal("25.0"), new BigDecimal("0.95"), false, null);
    }
}
