package com.luna.aggarly.review.service;

import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    ReviewResponse createReview(CreateReviewRequest request, UUID guestId);

    Page<ReviewResponse> getPropertyReviews(UUID propertyId, Pageable pageable);

    Page<ReviewResponse> getGuestReviews(UUID guestId, Pageable pageable);

    PropertyRatingSummaryResponse getPropertyRatingSummary(UUID propertyId);

    void deleteReview(UUID reviewId, UUID guestId);
}
