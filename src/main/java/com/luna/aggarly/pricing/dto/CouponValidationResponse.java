package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;

public record CouponValidationResponse(
        boolean valid,
        BigDecimal discountAmount,
        String message
) {
}
