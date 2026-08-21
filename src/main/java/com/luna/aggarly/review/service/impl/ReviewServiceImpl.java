package com.luna.aggarly.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.entity.Review;
import com.luna.aggarly.review.event.ReviewCreatedEvent;
import com.luna.aggarly.review.event.ReviewDeletedEvent;
import com.luna.aggarly.review.exceptions.AlreadyReviewedException;
import com.luna.aggarly.review.exceptions.ReviewNotFoundException;
import com.luna.aggarly.review.exceptions.UncompletedStayReviewException;
import com.luna.aggarly.review.mapper.ReviewMapper;
import com.luna.aggarly.review.repository.ReviewRepository;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, UUID guestId) {
        log.info("Creating review for propertyId={}, bookingId={}, guestId={}", request.propertyId(), request.bookingId(), guestId);

        if (!bookingService.hasCompletedStay(guestId, request.propertyId())) {
            throw new UncompletedStayReviewException();
        }

        if (reviewRepository.existsByBookingId(request.bookingId())) {
            throw new AlreadyReviewedException(request.bookingId());
        }

        Review review = Review.builder()
                .propertyId(request.propertyId())
                .bookingId(request.bookingId())
                .guestId(guestId)
                .rating(request.rating())
                .comment(request.comment())
                .cleanlinessRating(request.cleanlinessRating())
                .accuracyRating(request.accuracyRating())
                .checkInRating(request.checkInRating())
                .communicationRating(request.communicationRating())
                .locationRating(request.locationRating())
                .valueRating(request.valueRating())
                .build();

        review = reviewRepository.save(review);
        eventPublisher.publishEvent(new ReviewCreatedEvent(this, request.propertyId(), request.rating()));

        return reviewMapper.toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getPropertyReviews(UUID propertyId, Pageable pageable) {
        return reviewRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getGuestReviews(UUID guestId, Pageable pageable) {
        return reviewRepository.findByGuestIdOrderByCreatedAtDesc(guestId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyRatingSummaryResponse getPropertyRatingSummary(UUID propertyId) {
        Object[] result = reviewRepository.getRatingSummaryByPropertyId(propertyId);
        BigDecimal avgRating = BigDecimal.ZERO;
        long totalReviews = 0;

        if (result != null && result.length > 0 && result[0] != null) {
            Double avg = (Double) result[0];
            totalReviews = ((Number) result[1]).longValue();
            avgRating = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        }

        return new PropertyRatingSummaryResponse(propertyId, avgRating, totalReviews);
    }

    @Override
    @Transactional
    public void deleteReview(UUID reviewId, UUID guestId) {
        log.info("Deleting reviewId={} by userId={}", reviewId, guestId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        if (!review.getGuestId().equals(guestId) && !SecurityUtils.hasRole("ADMIN")) {
            throw new ReviewNotFoundException(reviewId);
        }

        reviewRepository.delete(review);
        eventPublisher.publishEvent(new ReviewDeletedEvent(this, review.getPropertyId()));
    }
}
