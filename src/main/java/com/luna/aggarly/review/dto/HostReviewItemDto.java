package com.luna.aggarly.review.dto;

import java.time.Instant;
import java.util.UUID;

public record HostReviewItemDto(
        UUID id,
        UUID propertyId,
        String propertyTitle,
        UUID guestId,
        String guestName,
        String guestInitials,
        int rating,
        String comment,
        String stayDates,
        boolean isFeatured,
        Integer cleanlinessRating,
        Integer accuracyRating,
        Integer quietudeRating,
        Integer opticsRating,
        Integer checkInRating,
        Integer communicationRating,
        CuratorResponseDto curatorResponse,
        Instant createdAt
) {
    public record CuratorResponseDto(
            String text,
            Instant respondedAt,
            String curatorName
    ) {}
}