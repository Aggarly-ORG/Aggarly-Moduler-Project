package com.luna.aggarly.scheduler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.booking.event.BookingCancelledEvent;
import com.luna.aggarly.booking.event.BookingConfirmedEvent;
import com.luna.aggarly.payment.event.PaymentFailedEvent;
import com.luna.aggarly.payment.event.PaymentSucceededEvent;
import com.luna.aggarly.scheduler.entity.ScheduledEventTrigger;
import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.repository.ScheduledEventTriggerRepository;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledEventProcessor {

    private final ScheduledEventTriggerRepository triggerRepository;
    private final WorkflowTaskExecutor workflowTaskExecutor;
    private final ObjectMapper objectMapper;

    @Async
    public void processEvent(String eventType, Object eventPayload) {
        log.info("Processing event trigger for eventType: {}", eventType);
        List<ScheduledEventTrigger> triggers = triggerRepository.findActiveByEventType(eventType);

        if (triggers.isEmpty()) {
            return;
        }

        for (ScheduledEventTrigger trigger : triggers) {
            ScheduledTask task = trigger.getTask();
            if (task == null || !task.getStatus().name().equals("ACTIVE")) {
                continue;
            }

            if (!matchesFilter(trigger.getFilterConfig(), eventPayload)) {
                continue;
            }

            // Tenant isolation check
            if (!isAuthorizedForEvent(task.getUserId(), eventPayload)) {
                log.warn("Event owner does not match task owner for task {} on event {}", task.getId(), eventType);
                continue;
            }

            try {
                ExecutionContext context = ExecutionContext.forEvent(
                        task.getUserId(),
                        task.getId(),
                        UUID.randomUUID(),
                        eventPayload,
                        task.getTimezone()
                );
                workflowTaskExecutor.execute(task, context);
            } catch (Exception ex) {
                log.error("Failed to execute event-triggered task {}", task.getId(), ex);
            }
        }
    }

    private boolean matchesFilter(String filterConfigJson, Object eventPayload) {
        if (filterConfigJson == null || filterConfigJson.isBlank() || filterConfigJson.equals("{}")) {
            return true;
        }
        try {
            JsonNode filterNode = objectMapper.readTree(filterConfigJson);
            JsonNode eventNode = objectMapper.valueToTree(eventPayload);

            var fields = filterNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey();
                JsonNode expected = entry.getValue();
                JsonNode actual = eventNode.get(key);

                if (actual == null || !actual.asText().equalsIgnoreCase(expected.asText())) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Error evaluating event filter: {}", ex.getMessage());
            return true;
        }
    }

    private boolean isAuthorizedForEvent(UUID taskUserId, Object eventPayload) {
        try {
            JsonNode eventNode = objectMapper.valueToTree(eventPayload);
            if (eventNode.has("hostId")) {
                String hostId = eventNode.get("hostId").asText();
                if (taskUserId != null && !taskUserId.toString().equalsIgnoreCase(hostId)) {
                    return false;
                }
            }
            if (eventNode.has("userId")) {
                String userId = eventNode.get("userId").asText();
                if (taskUserId != null && !taskUserId.toString().equalsIgnoreCase(userId)) {
                    return false;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        processEvent("BOOKING.CONFIRMED", Map.of("bookingId", event.getBookingId().toString()));
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        processEvent("BOOKING.CANCELLED", Map.of(
                "bookingId", event.getBookingId().toString(),
                "reason", event.getReason() != null ? event.getReason() : "",
                "refundAmount", event.getRefundAmount() != null ? event.getRefundAmount().doubleValue() : 0.0
        ));
    }

    @EventListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        processEvent("PAYMENT.SUCCEEDED", Map.of("bookingId", event.getBookingId().toString(), "amount", event.getAmount() != null ? event.getAmount().doubleValue() : 0.0));
    }

    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        processEvent("PAYMENT.FAILED", Map.of(
                "bookingId", event.getBookingId().toString(),
                "errorCode", event.getErrorCode() != null ? event.getErrorCode() : "",
                "errorMessage", event.getErrorMessage() != null ? event.getErrorMessage() : ""
        ));
    }
}
