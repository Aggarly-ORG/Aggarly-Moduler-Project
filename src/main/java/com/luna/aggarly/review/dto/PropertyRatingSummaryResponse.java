package com.luna.aggarly.review.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertyRatingSummaryResponse(
        UUID propertyId,
        BigDecimal avgRating,
        long totalReviews
) {
}
