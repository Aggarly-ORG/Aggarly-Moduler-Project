package com.luna.aggarly.scheduler.dto;

import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.MisfirePolicy;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.Map;

@Builder
public record CreateScheduledTaskRequest(
        @NotBlank(message = "Task name is required")
        String name,

        String description,

        @NotNull(message = "Trigger type is required")
        TriggerType triggerType,

        ExecutionType executionType,

        MisfirePolicy misfirePolicy,

        String timezone,

        Map<String, Object> triggerConfig,

        @NotNull(message = "Workflow plan is required")
        Map<String, Object> plan,

        Integer maxRetries
) {}
