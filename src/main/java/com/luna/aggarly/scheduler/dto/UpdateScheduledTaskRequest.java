package com.luna.aggarly.scheduler.dto;

import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import lombok.Builder;

import java.util.Map;

@Builder
public record UpdateScheduledTaskRequest(
        String name,
        String description,
        TriggerType triggerType,
        String timezone,
        Map<String, Object> triggerConfig,
        Map<String, Object> plan,
        Integer maxRetries
) {}
