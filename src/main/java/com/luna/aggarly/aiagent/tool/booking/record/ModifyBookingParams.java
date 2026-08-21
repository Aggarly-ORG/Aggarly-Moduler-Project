package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ModifyBookingParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier of the booking reservation to modify.")
        UUID bookingId,

        @NotBlank
        @JsonPropertyDescription("New check-in date in YYYY-MM-DD format.")
        String newCheckIn,

        @NotBlank
        @JsonPropertyDescription("New check-out date in YYYY-MM-DD format.")
        String newCheckOut
) {}
