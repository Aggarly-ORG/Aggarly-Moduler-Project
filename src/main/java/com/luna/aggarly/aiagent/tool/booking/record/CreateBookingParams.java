package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBookingParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier of the target property to book.")
        UUID propertyId,

        @NotBlank
        @JsonPropertyDescription("Check-in date formatted as YYYY-MM-DD.")
        String checkIn,

        @NotBlank
        @JsonPropertyDescription("Check-out date formatted as YYYY-MM-DD.")
        String checkOut,

        @JsonPropertyDescription("Number of guests staying at the property.")
        int guests
) {}
