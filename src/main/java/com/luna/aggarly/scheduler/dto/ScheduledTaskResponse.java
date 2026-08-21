package com.luna.aggarly.scheduler.dto;

import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.MisfirePolicy;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record ScheduledTaskResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        TaskStatus status,
        TriggerType triggerType,
        ExecutionType executionType,
        MisfirePolicy misfirePolicy,
        String timezone,
        Instant nextExecutionAt,
        Instant lastExecutionAt,
        Instant lastStartedAt,
        Instant lastFinishedAt,
        Integer retryCount,
        Integer maxRetries,
        String lastError,
        Map<String, Object> triggerConfig,
        Map<String, Object> plan,
        Integer planVersion,
        Instant createdAt,
        Instant updatedAt
) {}
