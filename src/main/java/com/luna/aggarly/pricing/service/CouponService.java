package com.luna.aggarly.pricing.service;

import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.dto.CouponValidationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface CouponService {
    CouponResponse createCoupon(CouponRequest request);
    void redeemCoupon(String code, UUID userId, UUID bookingId);
    CouponValidationResponse validateCoupon(String code, BigDecimal subtotal, UUID userId);
    Page<CouponResponse> getAllCoupons(Pageable pageable);
    Page<CouponResponse> getAllCoupons(String tier, Boolean seasonal, Pageable pageable);
    void deactivateCoupon(UUID couponId);
    com.luna.aggarly.pricing.dto.CouponMetricsResponse getMetrics();
}
