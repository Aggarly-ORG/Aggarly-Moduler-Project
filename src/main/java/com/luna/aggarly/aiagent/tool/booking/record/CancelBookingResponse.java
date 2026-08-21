package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record CancelBookingResponse(
        @JsonPropertyDescription("The unique identifier of the cancelled booking.")
        UUID bookingId,

        @JsonPropertyDescription("Status message describing the cancellation output.")
        String statusMessage,

        @JsonPropertyDescription("Calculated refund amount in USD based on policy rules.")
        double refundAmount
) {}
