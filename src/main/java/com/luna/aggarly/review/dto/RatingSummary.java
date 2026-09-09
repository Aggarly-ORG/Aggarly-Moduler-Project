package com.luna.aggarly.review.dto;

public record RatingSummary(
        Double averageRating,
        Long totalReviews
) {
    public RatingSummary {
        totalReviews = (totalReviews != null) ? totalReviews : 0L;
    }

    public double averageRatingOrZero() {
        return averageRating != null ? averageRating : 0.0;
    }
}
