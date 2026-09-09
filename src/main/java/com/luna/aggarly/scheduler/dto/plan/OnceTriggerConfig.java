package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.OffsetDateTime;

public record OnceTriggerConfig(

        @NotNull
        @JsonPropertyDescription("Exact date and time at which the task should execute.")
        Instant executeAt

) implements TriggerConfig {
}
