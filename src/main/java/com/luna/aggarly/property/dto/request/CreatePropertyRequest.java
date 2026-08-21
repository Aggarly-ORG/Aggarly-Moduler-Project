package com.luna.aggarly.property.dto.request;

import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.entity.enums.PropertyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record CreatePropertyRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 20, message = "Description must be at least 20 characters")
        String description,

        @NotNull(message = "Property type is required")
        PropertyType propertyType,

        @Min(value = 1, message = "Must allow at least 1 guest")
        int maxGuests,

        @Min(value = 0, message = "Bedrooms cannot be negative")
        int bedrooms,

        @Min(value = 0, message = "Bathrooms cannot be negative")
        int bathrooms,

        @NotNull(message = "Base price per night is required")
        @Min(value = 1, message = "Price must be greater than 0")
        BigDecimal basePricePerNight,

        @NotNull(message = "Cancellation policy is required")
        CancellationPolicy cancellationPolicy,

        BigDecimal latitude,
        BigDecimal longitude,

        @NotNull(message = "Address details are required")
        AddressRequest address,

        Set<UUID> amenityIds,

        Set<String> imageKeys
) {
}
