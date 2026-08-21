package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PriceExplanationParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier of the property.")
        UUID propertyId,

        @NotBlank
        @JsonPropertyDescription("Check-in date in YYYY-MM-DD format.")
        String checkIn,

        @NotBlank
        @JsonPropertyDescription("Check-out date in YYYY-MM-DD format.")
        String checkOut,

        @JsonPropertyDescription("Optional coupon discount code.")
        String couponCode
) {}
