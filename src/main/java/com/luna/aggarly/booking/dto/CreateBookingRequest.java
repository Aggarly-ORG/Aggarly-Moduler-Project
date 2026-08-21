package com.luna.aggarly.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        @NotNull(message = "Check-in date is required")
        @Future(message = "Check-in date must be in the future")
        LocalDate checkIn,

        @NotNull(message = "Check-out date is required")
        @Future(message = "Check-out date must be in the future")
        LocalDate checkOut,

        @Min(value = 1, message = "Guest count must be at least 1")
        int guestCount,

        String couponCode
) {
}
