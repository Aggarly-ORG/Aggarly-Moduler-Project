package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventOffsetTriggerConfig(

        @NotBlank
        @JsonPropertyDescription("Registered event that provides the reference date/time.")
        String event,

        @NotBlank
        @JsonPropertyDescription("Field in the event payload used as the reference date/time (e.g. checkInDate).")
        String reference,

        @NotNull
        @Valid
        @JsonPropertyDescription("Offset applied to the event reference.")
        EventOffset offset

) implements TriggerConfig {
}
