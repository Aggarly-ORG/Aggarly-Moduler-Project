package com.luna.aggarly.pricing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ValidateCouponRequest(
        @NotBlank(message = "Coupon code is required")
        String code,

        @NotNull(message = "Subtotal is required")
        BigDecimal subtotal
) {
}
