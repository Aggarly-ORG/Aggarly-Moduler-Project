package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityCheckParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier (UUID) of the property to check availability for.")
        UUID propertyId,

        @NotNull
        @JsonPropertyDescription("Check-in date in YYYY-MM-DD format.")
        LocalDate checkIn,

        @NotNull
        @JsonPropertyDescription("Check-out date in YYYY-MM-DD format.")
        LocalDate checkOut
) {}
