package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CancelBookingParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier of the booking reservation to cancel.")
        UUID bookingId,

        @JsonPropertyDescription("Optional reason for the cancellation.")
        String reason
) {}
