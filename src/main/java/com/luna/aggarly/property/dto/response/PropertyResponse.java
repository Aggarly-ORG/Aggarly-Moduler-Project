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
        Set<AmenityResponse> amenities
) {
}
