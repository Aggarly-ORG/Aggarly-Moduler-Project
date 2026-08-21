package com.luna.aggarly.booking.dto;

import java.math.BigDecimal;

public record CancellationQuoteResponse(
        BigDecimal refundPercentage,
        BigDecimal refundAmount,
        String policyExplanation
) {
}
