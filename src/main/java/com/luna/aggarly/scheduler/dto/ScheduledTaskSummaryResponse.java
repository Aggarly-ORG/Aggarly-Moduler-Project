package com.luna.aggarly.scheduler.dto;

import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ScheduledTaskSummaryResponse(
        UUID id,
        String name,
        String description,
        TaskStatus status,
        TriggerType triggerType,
        String timezone,
        Instant nextExecutionAt,
        Instant lastExecutionAt,
        Instant createdAt
) {}
