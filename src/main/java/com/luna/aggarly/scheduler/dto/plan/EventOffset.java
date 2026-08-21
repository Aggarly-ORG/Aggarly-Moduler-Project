package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.luna.aggarly.scheduler.entity.enums.EventOffsetUnit;
import jakarta.validation.constraints.NotNull;

public record EventOffset(

        @JsonPropertyDescription("Positive or negative offset relative to the event reference time.")
        long value,

        @NotNull
        @JsonPropertyDescription("Unit of the offset (MINUTES, HOURS, DAYS, WEEKS).")
        EventOffsetUnit unit

) {
}
