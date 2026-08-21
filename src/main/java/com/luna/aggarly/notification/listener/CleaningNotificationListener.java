package com.luna.aggarly.notification.listener;

import com.luna.aggarly.cleaning.event.CleaningCompletedEvent;
import com.luna.aggarly.cleaning.event.CleaningIssueReportedEvent;
import com.luna.aggarly.cleaning.event.CleaningTaskCreatedEvent;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleaningNotificationListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleaningTaskCreated(CleaningTaskCreatedEvent event) {
        log.info("Processing CleaningTaskCreatedEvent for task {}", event.getTaskId());

        if (event.getAssignedCleanerId() != null) {
            dispatcher.dispatch(
                    event.getAssignedCleanerId(),
                    NotificationCategory.CLEANING,
                    "CLEANING_TASK_ASSIGNED",
                    "New Cleaning Task Assigned",
                    "You have been assigned a " + event.getTaskType() + " cleaning task scheduled for " + event.getScheduledDate() + ".",
                    "{\"taskId\":\"" + event.getTaskId() + "\",\"propertyId\":\"" + event.getPropertyId() + "\"}",
                    null,
                    null
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleaningCompleted(CleaningCompletedEvent event) {
        log.info("Processing CleaningCompletedEvent for task {}", event.getTaskId());

        Map<String, Object> model = new HashMap<>();
        model.put("recipientName", "Host");
        model.put("title", "Turnover Cleaning Completed");
        model.put("message", "The turnover cleaning task for your property has been completed and is ready for inspection.");

        dispatcher.dispatch(
                event.getHostId(),
                NotificationCategory.CLEANING,
                "CLEANING_COMPLETED",
                "Cleaning Turnover Completed",
                "Your property has been cleaned and marked ready for next guest check-in.",
                "{\"taskId\":\"" + event.getTaskId() + "\",\"propertyId\":\"" + event.getPropertyId() + "\"}",
                "cleaning-alert",
                model
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleaningIssueReported(CleaningIssueReportedEvent event) {
        log.warn("Processing CleaningIssueReportedEvent for issue {}", event.getIssueId());

        Map<String, Object> model = new HashMap<>();
        model.put("recipientName", "Host");
        model.put("title", event.getTitle());
        model.put("severity", event.getSeverity().name());
        model.put("description", "A cleaner reported: " + event.getTitle());

        dispatcher.dispatch(
                event.getHostId(),
                NotificationCategory.CLEANING,
                "CLEANING_ISSUE_REPORTED",
                "⚠️ Damage/Maintenance Reported: " + event.getTitle(),
                "A " + event.getSeverity() + " severity issue was reported during cleaning: " + event.getTitle(),
                "{\"issueId\":\"" + event.getIssueId() + "\",\"propertyId\":\"" + event.getPropertyId() + "\"}",
                "damage-alert",
                model
        );
    }
}
