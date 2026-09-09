package com.luna.aggarly.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.dto.RatingSummary;
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
    private final com.luna.aggarly.property.repository.PropertyRepository propertyRepository;
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
        RatingSummary summary = reviewRepository.getRatingSummaryByPropertyId(propertyId);
        BigDecimal avgRating = BigDecimal.ZERO;
        long totalReviews = 0;

        if (summary != null && summary.averageRating() != null) {
            totalReviews = summary.totalReviews();
            avgRating = BigDecimal.valueOf(summary.averageRating()).setScale(2, RoundingMode.HALF_UP);
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

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.luna.aggarly.review.dto.HostReviewItemDto> getHostReviews(UUID hostId, String filter) {
        java.util.List<com.luna.aggarly.property.entity.Property> properties = propertyRepository.findByHostId(hostId);
        if (properties.isEmpty()) {
            return java.util.List.of();
        }

        java.util.Map<UUID, String> propTitleMap = properties.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.luna.aggarly.property.entity.Property::getId,
                        com.luna.aggarly.property.entity.Property::getTitle,
                        (a, b) -> a
                ));

        java.util.List<UUID> propIds = new java.util.ArrayList<>(propTitleMap.keySet());
        java.util.List<Review> reviews = reviewRepository.findByPropertyIdInOrderByCreatedAtDesc(propIds);

        if (filter != null && !filter.isBlank() && !filter.equalsIgnoreCase("ALL")) {
            String f = filter.trim().toUpperCase();
            reviews = reviews.stream().filter(r -> {
                if ("NEEDS_RESPONSE".equals(f) || "UNANSWERED".equals(f)) {
                    return r.getHostResponse() == null || r.getHostResponse().isBlank();
                } else if ("FEATURED".equals(f) || "FIVE_STAR".equals(f)) {
                    return r.getRating() >= 5;
                } else if ("CRITICAL".equals(f)) {
                    return r.getRating() <= 3;
                }
                return true;
            }).toList();
        }

        return reviews.stream().map(r -> {
            String title = propTitleMap.getOrDefault(r.getPropertyId(), "Sanctuary");
            com.luna.aggarly.review.dto.HostReviewItemDto.CuratorResponseDto curatorResp = null;
            if (r.getHostResponse() != null && !r.getHostResponse().isBlank()) {
                curatorResp = new com.luna.aggarly.review.dto.HostReviewItemDto.CuratorResponseDto(
                        r.getHostResponse(),
                        r.getHostRespondedAt() != null ? r.getHostRespondedAt() : r.getUpdatedAt(),
                        "Lead Sanctuary Curator"
                );
            }

            return new com.luna.aggarly.review.dto.HostReviewItemDto(
                    r.getId(),
                    r.getPropertyId(),
                    title,
                    r.getGuestId(),
                    "Resident Astronomer",
                    "RA",
                    r.getRating(),
                    r.getComment(),
                    "Completed Residency",
                    r.getRating() >= 5,
                    r.getCleanlinessRating(),
                    r.getAccuracyRating(),
                    r.getQuietudeRating(),
                    r.getOpticsRating(),
                    r.getCheckInRating(),
                    r.getCommunicationRating(),
                    curatorResp,
                    r.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void respondToReview(UUID reviewId, UUID hostId, String response) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        com.luna.aggarly.property.entity.Property prop = propertyRepository.findById(review.getPropertyId())
                .orElseThrow(() -> new com.luna.aggarly.property.exceptions.PropertyNotFoundException(review.getPropertyId()));

        if (!prop.getHostId().equals(hostId) && !SecurityUtils.hasRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Only the sanctuary curator can post a public response");
        }

        review.setHostResponse(response);
        review.setHostRespondedAt(java.time.Instant.now());
        reviewRepository.save(review);
    }
}
