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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailOperation implements WorkflowOperation {

    private final NotificationDispatcher notificationDispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "notification.sendEmail";
    }

    @Override
    public String description() {
        return "Sends a templated email with structured report or notification data.";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("userId").put("type", "string");
        props.putObject("template").put("type", "string");
        props.putObject("data").put("type", "object");
        return root;
    }

    @Override
    public JsonNode responseSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("sent").put("type", "boolean");
        props.putObject("idempotencyKey").put("type", "string");
        return root;
    }

    @Override
    public Object execute(Object arguments, ExecutionContext context) {
        UUID targetUserId = context.userId();
        String template = "GENERIC_AUTOMATION_REPORT";
        Map<String, Object> data = new HashMap<>();

        if (arguments instanceof Map<?, ?> map) {
            if (map.containsKey("userId") && map.get("userId") != null) {
                try {
                    targetUserId = UUID.fromString(String.valueOf(map.get("userId")));
                } catch (Exception ignored) {}
            }
            if (map.containsKey("template") && map.get("template") != null) {
                template = String.valueOf(map.get("template"));
            }
            if (map.containsKey("data") && map.get("data") instanceof Map<?, ?> dataMap) {
                for (Map.Entry<?, ?> e : dataMap.entrySet()) {
                    data.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }

        String idempotencyKey = String.format("task_%s_exec_%s", context.taskId(), context.executionId());
        data.put("idempotencyKey", idempotencyKey);

        log.info("Executing notification.sendEmail to user {} using template '{}'", targetUserId, template);
        notificationDispatcher.dispatch(
                targetUserId,
                NotificationCategory.ALERTS,
                "AUTOMATION_EMAIL",
                "Aggarly Automation Report: " + template,
                "Your scheduled report is ready.",
                objectMapper.valueToTree(data).toString(),
                template,
                data
        );

        return Map.of(
                "sent", true,
                "template", template,
                "idempotencyKey", idempotencyKey
        );
    }
}
