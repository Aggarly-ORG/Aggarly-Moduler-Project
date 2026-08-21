package com.luna.aggarly.property.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddPropertyImageRequest(
        @NotBlank(message = "Object key is required")
        @Size(max = 1024, message = "Object key must not exceed 1024 characters")
        String objectKey,

        boolean isCover
) {
}
