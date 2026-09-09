package com.luna.aggarly.property.listener;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.review.dto.RatingSummary;
import com.luna.aggarly.review.event.ReviewCreatedEvent;
import com.luna.aggarly.review.event.ReviewDeletedEvent;
import com.luna.aggarly.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyRatingEventListener {

    private final PropertyRepository propertyRepository;
    private final ReviewRepository reviewRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreatedEvent event) {
        log.info("Recalculating property ratings for propertyId={} after review creation", event.getPropertyId());
        recalculatePropertyRating(event.getPropertyId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewDeleted(ReviewDeletedEvent event) {
        log.info("Recalculating property ratings for propertyId={} after review deletion", event.getPropertyId());
        recalculatePropertyRating(event.getPropertyId());
    }

    private void recalculatePropertyRating(UUID propertyId) {
        propertyRepository.findById(propertyId).ifPresent(property -> {
            RatingSummary summary = reviewRepository.getRatingSummaryByPropertyId(propertyId);
            if (summary != null && summary.averageRating() != null) {
                property.setAvgRating(BigDecimal.valueOf(summary.averageRating()).setScale(2, RoundingMode.HALF_UP));
                property.setReviewCount(summary.totalReviews().intValue());
            } else {
                property.setAvgRating(BigDecimal.ZERO);
                property.setReviewCount(0);
            }
            propertyRepository.save(property);
        });
    }
}
