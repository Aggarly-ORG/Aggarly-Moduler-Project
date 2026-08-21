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
public class NotificationSendOperation implements WorkflowOperation {

    private final NotificationDispatcher notificationDispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "notification.send";
    }

    @Override
    public String description() {
        return "Sends an in-app push/notification alert to a specific user.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("userId").put("type", "string");
        props.putObject("title").put("type", "string");
        props.putObject("message").put("type", "string");
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
        UUID targetUserId = context.userId();
        String title = "Automated Reminder";
        String message = "Your scheduled automation has executed.";

        if (arguments instanceof Map<?, ?> map) {
            if (map.containsKey("userId") && map.get("userId") != null) {
                try {
                    targetUserId = UUID.fromString(String.valueOf(map.get("userId")));
                } catch (Exception ignored) {}
            }
            if (map.containsKey("title") && map.get("title") != null) {
                title = String.valueOf(map.get("title"));
            }
            if (map.containsKey("message") && map.get("message") != null) {
                message = String.valueOf(map.get("message"));
            }
        }

        log.info("Executing notification.send for targetUserId={}, title='{}'", targetUserId, title);
        notificationDispatcher.dispatch(
                targetUserId,
                NotificationCategory.ALERTS,
                "AUTOMATION_NOTIFICATION",
                title,
                message,
                null,
                null,
                null
        );

        return Map.of("delivered", true, "userId", targetUserId.toString());
    }
}
