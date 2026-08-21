package com.luna.aggarly.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReviewRequest(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        @NotNull(message = "Booking ID is required")
        UUID bookingId,

        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        int rating,

        @NotBlank(message = "Comment is required")
        String comment,

        @Min(1) @Max(5) Integer cleanlinessRating,
        @Min(1) @Max(5) Integer accuracyRating,
        @Min(1) @Max(5) Integer checkInRating,
        @Min(1) @Max(5) Integer communicationRating,
        @Min(1) @Max(5) Integer locationRating,
        @Min(1) @Max(5) Integer valueRating
) {
}
