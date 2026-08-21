package com.luna.aggarly.property.dto.request;

import com.luna.aggarly.property.entity.enums.AmenityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAmenityRequest(
        @NotBlank(message = "Amenity name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 100, message = "Icon name must not exceed 100 characters")
        String icon,

        @NotNull(message = "Amenity category is required")
        AmenityCategory category
) {
}
