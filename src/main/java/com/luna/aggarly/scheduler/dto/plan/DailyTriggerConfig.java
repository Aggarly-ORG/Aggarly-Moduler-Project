package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;

public record DailyTriggerConfig(

        @NotBlank
        @JsonPropertyDescription("Local time in HH:mm format.")
        String time

) implements TriggerConfig {
}
