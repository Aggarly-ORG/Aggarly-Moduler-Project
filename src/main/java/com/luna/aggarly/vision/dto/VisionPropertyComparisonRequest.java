package com.luna.aggarly.vision.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record VisionPropertyComparisonRequest(
        @NotEmpty(message = "Property IDs list cannot be empty")
        List<UUID> propertyIds
) {}
