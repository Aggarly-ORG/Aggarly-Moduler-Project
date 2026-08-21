package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(String code, String adjustmentType, BigDecimal adjustmentValue,
                             Instant expiresAt, Integer maxRedemptions, BigDecimal minSubtotal) {}
