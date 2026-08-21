package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.luna.aggarly.scheduler.entity.enums.IntervalUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IntervalTriggerConfig(

        @Min(1)
        @JsonPropertyDescription("Number of units between executions.")
        long every,

        @NotNull
        @JsonPropertyDescription("Unit used for the interval (MINUTES, HOURS, DAYS, WEEKS).")
        IntervalUnit unit

) implements TriggerConfig {
}
