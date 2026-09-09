package com.luna.aggarly.host.dto;

import java.math.BigDecimal;

public record SettlementLedgerItemDto(
        String id,
        String sanctuaryId,
        String sanctuaryTitle,
        String sanctuaryLocation,
        String guestName,
        String bookingRef,
        String stayDates,
        int nightsCount,
        BigDecimal grossAmount,
        BigDecimal takeRateAmount,
        BigDecimal netPayout,
        String currency,
        String status,
        String releaseDate,
        String paymentMethodMasked
) {}