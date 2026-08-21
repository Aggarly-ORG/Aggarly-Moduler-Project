package com.luna.aggarly.property.dto.request;

import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.entity.enums.PropertyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record UpdatePropertyRequest(
        @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
        String title,

        @Size(min = 20, message = "Description must be at least 20 characters")
        String description,

        PropertyType propertyType,

        @Min(value = 1, message = "Must allow at least 1 guest")
        Integer maxGuests,

        @Min(value = 0, message = "Bedrooms cannot be negative")
        Integer bedrooms,

        @Min(value = 0, message = "Bathrooms cannot be negative")
        Integer bathrooms,

        @Min(value = 1, message = "Price must be greater than 0")
        BigDecimal basePricePerNight,

        CancellationPolicy cancellationPolicy,

        PropertyStatus status,

        Set<UUID> amenityIds
) {
}
