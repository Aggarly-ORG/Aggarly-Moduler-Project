package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;

public record EventTriggerConfig(

        @NotBlank
        @JsonPropertyDescription("Registered application event, for example BOOKING.CREATED or PAYMENT.SUCCEEDED.")
        String event

) implements TriggerConfig {
}
