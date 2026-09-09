package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;

public record CouponMetricsResponse(
        BigDecimal grossDiscountGranted,
        long activeCampaignsCount,
        double redemptionRate,
        String topCampaign
) {}