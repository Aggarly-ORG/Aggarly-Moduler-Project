package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(UUID id, String code, String adjustmentType, BigDecimal adjustmentValue,
                              Instant expiresAt, Integer maxRedemptions, int currentRedemptions, boolean active) {}
