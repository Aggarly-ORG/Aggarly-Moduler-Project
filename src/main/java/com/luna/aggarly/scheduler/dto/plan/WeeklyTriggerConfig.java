package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotEmpty;

import java.time.DayOfWeek;
import java.util.List;

public record WeeklyTriggerConfig(

        @NotEmpty
        @JsonPropertyDescription("Days of the week on which the task should execute.")
        List<DayOfWeek> days,

        @JsonPropertyDescription("Local time in HH:mm format.")
        String time

) implements TriggerConfig {
}
