package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record CheckInInstructionsResponse(
        @JsonPropertyDescription("The unique identifier of the booking.")
        UUID bookingId,

        @JsonPropertyDescription("Smart lock keyless pin code.")
        String lockCode,

        @JsonPropertyDescription("Wi-Fi network name.")
        String wifiNetwork,

        @JsonPropertyDescription("Wi-Fi network password.")
        String wifiPassword,

        @JsonPropertyDescription("Assigned parking spot details.")
        String parkingSpot,

        @JsonPropertyDescription("Detailed step-by-step check-in guide.")
        String checkInGuide
) {}
