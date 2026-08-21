package com.luna.aggarly.notification.listener;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.review.event.ReviewCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final PropertyService propertyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewCreated(ReviewCreatedEvent event) {
        log.info("Processing ReviewCreatedEvent for property {}", event.getPropertyId());

        try {
            PropertyResponse property = propertyService.getPropertyById(event.getPropertyId());
            dispatcher.dispatch(
                    property.hostId(),
                    NotificationCategory.REVIEWS,
                    "NEW_REVIEW_RECEIVED",
                    "New " + event.getRating() + "-Star Review Received ⭐",
                    "A guest left a " + event.getRating() + "-star review on '" + property.title() + "'.",
                    "{\"propertyId\":\"" + event.getPropertyId() + "\"}",
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("Failed to process review notification for property {}", event.getPropertyId(), e);
        }
    }
}
