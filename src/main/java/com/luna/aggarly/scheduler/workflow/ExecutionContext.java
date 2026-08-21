package com.luna.aggarly.scheduler.workflow;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ExecutionContext(
        UUID userId,
        UUID taskId,
        UUID executionId,
        Object event,
        Map<String, Object> stepResults,
        Instant executionTime,
        String timezone,
        Map<String, Object> rootObject
) {
    public ExecutionContext {
        if (stepResults == null) {
            stepResults = new HashMap<>();
        }
        if (executionTime == null) {
            executionTime = Instant.now();
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }
        if (rootObject == null) {
            rootObject = new HashMap<>();
        }
    }

    public static ExecutionContext forTask(UUID userId, UUID taskId, UUID executionId, String timezone) {
        return new ExecutionContext(userId, taskId, executionId, null, new HashMap<>(), Instant.now(), timezone, new HashMap<>());
    }

    public static ExecutionContext forEvent(UUID userId, UUID taskId, UUID executionId, Object event, String timezone) {
        return new ExecutionContext(userId, taskId, executionId, event, new HashMap<>(), Instant.now(), timezone, new HashMap<>());
    }

    public static ExecutionContext withRoot(UUID userId, UUID taskId, UUID executionId, Object event, String timezone, Map<String, Object> rootObject) {
        return new ExecutionContext(userId, taskId, executionId, event, new HashMap<>(), Instant.now(), timezone, rootObject);
    }
}
