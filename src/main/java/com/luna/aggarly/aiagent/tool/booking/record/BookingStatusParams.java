package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookingStatusParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier of the booking reservation.")
        UUID bookingId
) {}
