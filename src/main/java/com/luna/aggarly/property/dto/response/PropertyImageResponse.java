package com.luna.aggarly.property.dto.response;

import java.util.UUID;

public record PropertyImageResponse(
        UUID id,
        String objectKey,
        int displayOrder,
        boolean isCover
) {
}
