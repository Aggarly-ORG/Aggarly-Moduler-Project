package com.luna.aggarly.scheduler.workflow;

import com.luna.aggarly.common.expression.spi.ExpressionContext;

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
        Map<String, Object> rootObject,
        Map<String, Object> variables
) implements ExpressionContext {

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
        if (variables == null) {
            variables = new HashMap<>();
        }
    }

    public ExecutionContext(UUID userId, UUID taskId, UUID executionId, Object event, Map<String, Object> stepResults, Instant executionTime, String timezone, Map<String, Object> rootObject) {
        this(userId, taskId, executionId, event, stepResults, executionTime, timezone, rootObject, new HashMap<>());
    }

    public static ExecutionContext forTask(UUID userId, UUID taskId, UUID executionId, String timezone) {
        return new ExecutionContext(userId, taskId, executionId, null, new HashMap<>(), Instant.now(), timezone, new HashMap<>(), new HashMap<>());
    }

    public static ExecutionContext forEvent(UUID userId, UUID taskId, UUID executionId, Object event, String timezone) {
        return new ExecutionContext(userId, taskId, executionId, event, new HashMap<>(), Instant.now(), timezone, new HashMap<>(), new HashMap<>());
    }

    public static ExecutionContext withRoot(UUID userId, UUID taskId, UUID executionId, Object event, String timezone, Map<String, Object> rootObject) {
        return new ExecutionContext(userId, taskId, executionId, event, new HashMap<>(), Instant.now(), timezone, rootObject, new HashMap<>());
    }

    @Override
    public Object getVariable(String name) {
        return variables != null ? variables.get(name) : null;
    }

    @Override
    public void setVariable(String name, Object value) {
        if (variables != null) {
            variables.put(name, value);
        }
    }
}
