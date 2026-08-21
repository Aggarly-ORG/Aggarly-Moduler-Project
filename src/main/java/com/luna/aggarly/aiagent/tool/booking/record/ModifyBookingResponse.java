package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record ModifyBookingResponse(
        @JsonPropertyDescription("The unique identifier of the modified booking.")
        UUID bookingId,

        @JsonPropertyDescription("Status of the modification operation.")
        String status,

        @JsonPropertyDescription("Price difference caused by date modification.")
        double priceDifference
) {}
