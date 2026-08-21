package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record BookingConfirmationResponse(
        @JsonPropertyDescription("Detailed status message of the confirmation result.")
        String statusMessage,

        @JsonPropertyDescription("Indicates whether the booking confirmation was successful.")
        boolean confirmed
) {}
