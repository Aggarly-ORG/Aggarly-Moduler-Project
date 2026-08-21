package com.luna.aggarly.aiagent.tool.schedule.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record CreateScheduleResponse(
        @JsonPropertyDescription("Unique identifier of the created scheduled task.")
        UUID taskId,

        @JsonPropertyDescription("Name of the scheduled task.")
        String name,

        @JsonPropertyDescription("Current status (e.g. ACTIVE, PAUSED).")
        String status,

        @JsonPropertyDescription("Trigger type used.")
        String triggerType,

        @JsonPropertyDescription("ISO timestamp of next scheduled run or description.")
        String nextExecutionAt,

        @JsonPropertyDescription("Human-readable confirmation message.")
        String message
) {}
