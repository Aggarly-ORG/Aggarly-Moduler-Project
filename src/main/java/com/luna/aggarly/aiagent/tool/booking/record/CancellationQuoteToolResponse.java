package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record CancellationQuoteToolResponse(
        @JsonPropertyDescription("The booking unique identifier.")
        UUID bookingId,

        @JsonPropertyDescription("Calculated refund amount in USD.")
        double refundAmount,

        @JsonPropertyDescription("Active cancellation policy name (e.g. FLEXIBLE, MODERATE, STRICT).")
        String policyName
) {}
