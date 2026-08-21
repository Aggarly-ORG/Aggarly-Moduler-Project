package com.luna.aggarly.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID propertyId,
        UUID bookingId,
        UUID guestId,
        int rating,
        String comment,
        Integer cleanlinessRating,
        Integer accuracyRating,
        Integer checkInRating,
        Integer communicationRating,
        Integer locationRating,
        Integer valueRating,
        Instant createdAt
) {
}
