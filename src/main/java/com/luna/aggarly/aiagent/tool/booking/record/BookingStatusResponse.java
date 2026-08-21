package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record BookingStatusResponse(
        @JsonPropertyDescription("The unique identifier of the booking.")
        UUID bookingId,

        @JsonPropertyDescription("Current state of the booking (e.g. CONFIRMED, PENDING, CANCELLED).")
        String status,

        @JsonPropertyDescription("Check-in date in YYYY-MM-DD format.")
        String checkInDate,

        @JsonPropertyDescription("Check-out date in YYYY-MM-DD format.")
        String checkOutDate
) {}
