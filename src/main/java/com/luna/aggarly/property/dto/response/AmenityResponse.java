package com.luna.aggarly.property.dto.response;

import com.luna.aggarly.property.entity.enums.AmenityCategory;
import java.util.UUID;

public record AmenityResponse(
        UUID id,
        String name,
        String icon,
        AmenityCategory category
) {
}
