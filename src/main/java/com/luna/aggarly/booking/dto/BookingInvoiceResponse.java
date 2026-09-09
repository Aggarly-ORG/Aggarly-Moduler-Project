package com.luna.aggarly.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BookingInvoiceResponse(
        String invoiceNumber,
        UUID bookingId,
        String sanctuaryTitle,
        String guestName,
        String guestEmail,
        LocalDate issueDate,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nightsCount,
        BigDecimal subtotal,
        BigDecimal vatRatePercentage,
        BigDecimal vatAmount,
        BigDecimal touristTax,
        BigDecimal cleaningFee,
        BigDecimal totalAmount,
        String currency,
        String escrowStatus,
        String paymentMethodMasked,
        String transactionRef
) {}