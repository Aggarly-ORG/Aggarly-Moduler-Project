package com.luna.aggarly.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID propertyId,
        LocalDate checkIn,
        LocalDate checkOut,
        int guestCount,
        String status,
        BigDecimal totalAmount,
        String currency,
        String clientSecret
) {
}
