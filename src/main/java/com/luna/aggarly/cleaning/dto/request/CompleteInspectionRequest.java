package com.luna.aggarly.cleaning.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CompleteInspectionRequest(
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must not exceed 5")
        Integer ratingByHost,

        String hostFeedback
) {}
