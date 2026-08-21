package com.luna.aggarly.aiagent.tool.schedule.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TaskActionParams(
        @NotNull
        @JsonPropertyDescription("The UUID of the scheduled task to operate on.")
        UUID taskId
) {}
