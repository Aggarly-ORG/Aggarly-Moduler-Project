package com.luna.aggarly.host.dto;

import java.math.BigDecimal;

public record HostFinancialsSummaryDto(
        BigDecimal grossBookingVolume,
        BigDecimal netHostEarnings,
        BigDecimal pendingEscrow,
        BigDecimal disbursedYtd,
        String currency,
        double takeRatePercentage,
        String bankAccountMasked,
        String nextScheduledPayoutDate,
        BigDecimal nextScheduledPayoutAmount
) {}