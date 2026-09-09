package com.luna.aggarly.payment.dto;

import java.math.BigDecimal;

public record AdminFinancialMetricsResponse(
    BigDecimal grossMerchandiseVolume,
    BigDecimal netCommissionTake,
    BigDecimal escrowClearingNext48h,
    BigDecimal totalRefundsSettled,
    double quotaPacingPercentage,
    double periodOverPeriodGrowthRate,
    String currency
) {}
