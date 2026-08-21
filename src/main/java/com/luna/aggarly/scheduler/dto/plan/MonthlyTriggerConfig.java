package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record MonthlyTriggerConfig(

        @Min(1)
        @Max(31)
        @JsonPropertyDescription("Day of the month on which the task should execute.")
        int dayOfMonth,

        @NotBlank
        @JsonPropertyDescription("Local time in HH:mm format.")
        String time

) implements TriggerConfig {
}
