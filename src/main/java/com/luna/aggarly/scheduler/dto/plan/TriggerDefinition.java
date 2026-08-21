package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TriggerDefinition(

        @NotNull
        @JsonPropertyDescription("How the task is triggered (ONCE, DAILY, WEEKLY, MONTHLY, INTERVAL, EVENT, EVENT_OFFSET).")
        TriggerType type,

        @NotNull
        @Valid
        @JsonPropertyDescription("Configuration corresponding to the selected trigger type.")
        TriggerConfig config

) {
}
