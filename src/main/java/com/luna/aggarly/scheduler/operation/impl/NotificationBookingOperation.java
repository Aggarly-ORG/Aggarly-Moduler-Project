package com.luna.aggarly.scheduler.operation.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.scheduler.operation.WorkflowOperation;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationBookingOperation implements WorkflowOperation {

    private final NotificationDispatcher notificationDispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "notification.sendBookingNotification";
    }

    @Override
    public String description() {
        return "Sends an instant alert to a host or guest regarding a booking event.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("hostId").put("type", "string");
        props.putObject("bookingId").put("type", "string");
        props.putObject("propertyId").put("type", "string");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("delivered").put("type", "boolean");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        UUID hostId = context.userId();
        String bookingId = "Unknown";
        String propertyId = "Unknown";

        if (arguments instanceof Map<?, ?> map) {
            if (map.containsKey("hostId") && map.get("hostId") != null) {
                try {
                    hostId = UUID.fromString(String.valueOf(map.get("hostId")));
                } catch (Exception ignored) {}
            }
            if (map.containsKey("bookingId") && map.get("bookingId") != null) {
                bookingId = String.valueOf(map.get("bookingId"));
            }
            if (map.containsKey("propertyId") && map.get("propertyId") != null) {
                propertyId = String.valueOf(map.get("propertyId"));
            }
        }

        log.info("Executing notification.sendBookingNotification for hostId={}, bookingId={}", hostId, bookingId);
        notificationDispatcher.dispatch(
                hostId,
                NotificationCategory.BOOKING,
                "BOOKING_AUTOMATION_ALERT",
                "New Booking Alert",
                String.format("A new reservation (%s) has been booked for property %s.", bookingId, propertyId),
                null,
                null,
                null
        );

        return Map.of("delivered", true, "bookingId", bookingId, "hostId", hostId.toString());
    }
}
