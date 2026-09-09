package com.luna.aggarly.pricing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.dto.CouponValidationResponse;
import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.Coupon;
import com.luna.aggarly.pricing.entity.CouponRedemption;
import com.luna.aggarly.pricing.event.CouponRedeemedEvent;
import com.luna.aggarly.pricing.exceptions.CouponAlreadyRedeemedException;
import com.luna.aggarly.pricing.exceptions.CouponExpiredException;
import com.luna.aggarly.pricing.exceptions.InvalidCouponException;
import com.luna.aggarly.pricing.mapper.PricingMapper;
import com.luna.aggarly.pricing.repository.CouponRedemptionRepository;
import com.luna.aggarly.pricing.repository.CouponRepository;
import com.luna.aggarly.pricing.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final PricingMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        log.info("Creating coupon code={}", request.code());
        Coupon coupon = mapper.toEntity(request);
        coupon.setCode(coupon.getCode().toUpperCase());
        return mapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public void redeemCoupon(String code, UUID userId, UUID bookingId) {
        log.info("Redeeming coupon code={} for userId={}, bookingId={}", code, userId, bookingId);
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(code.toUpperCase())
                .orElseThrow(() -> new InvalidCouponException("Invalid coupon code: " + code));

        try {
            redemptionRepository.save(CouponRedemption.builder()
                    .couponId(coupon.getId())
                    .userId(userId)
                    .bookingId(bookingId)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new CouponAlreadyRedeemedException(code);
        }

        coupon.setCurrentRedemptions(coupon.getCurrentRedemptions() + 1);
        couponRepository.save(coupon);
        eventPublisher.publishEvent(new CouponRedeemedEvent(this, coupon.getId(), userId, bookingId));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(String code, BigDecimal subtotal, UUID userId) {
        if (code == null || code.isBlank()) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Coupon code is empty");
        }

        Coupon coupon = couponRepository.findByCodeAndActiveTrue(code.toUpperCase())
                .orElse(null);

        if (coupon == null || !coupon.isActive()) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Invalid or inactive coupon code");
        }

        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(Instant.now())) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Coupon code has expired");
        }

        if (coupon.getMaxRedemptions() != null && coupon.getCurrentRedemptions() >= coupon.getMaxRedemptions()) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Coupon redemption limit reached");
        }

        if (coupon.getMinSubtotal() != null && subtotal.compareTo(coupon.getMinSubtotal()) < 0) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Subtotal does not meet minimum threshold of " + coupon.getMinSubtotal());
        }

        if (userId != null && redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            return new CouponValidationResponse(false, BigDecimal.ZERO, "Coupon already redeemed by user");
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getAdjustmentType() == AdjustmentType.PERCENTAGE) {
            discount = subtotal.multiply(coupon.getAdjustmentValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (coupon.getAdjustmentType() == AdjustmentType.FIXED_AMOUNT) {
            discount = coupon.getAdjustmentValue().min(subtotal);
        }

        return new CouponValidationResponse(true, discount, "Coupon valid");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> getAllCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> getAllCoupons(String tier, Boolean seasonal, Pageable pageable) {
        Page<Coupon> page = couponRepository.findAll(pageable);
        // If tier or seasonal filter requested, filter in memory or pass through
        if ((tier != null && !tier.isBlank()) || seasonal != null) {
            java.util.List<Coupon> filtered = page.getContent().stream()
                    .filter(c -> {
                        boolean match = true;
                        if (tier != null && !tier.isBlank()) {
                            match = match && c.getCode().contains(tier.toUpperCase());
                        }
                        if (seasonal != null && seasonal) {
                            match = match && (c.getCode().contains("SEASON") || c.getCode().contains("SOLSTICE") || c.getCode().contains("EQUINOX") || c.getCode().contains("SUMMER") || c.getCode().contains("WINTER"));
                        }
                        return match;
                    })
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size()).map(mapper::toResponse);
        }
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional
    public void deactivateCoupon(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new InvalidCouponException("Coupon not found: " + couponId));
        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public com.luna.aggarly.pricing.dto.CouponMetricsResponse getMetrics() {
        java.util.List<Coupon> coupons = couponRepository.findAll();
        long activeCount = coupons.stream().filter(Coupon::isActive).count();
        long totalRedemptions = coupons.stream().mapToInt(Coupon::getCurrentRedemptions).sum();

        BigDecimal grossDiscount = BigDecimal.ZERO;
        for (Coupon c : coupons) {
            if (c.getCurrentRedemptions() > 0) {
                if (c.getAdjustmentType() == AdjustmentType.FIXED_AMOUNT) {
                    grossDiscount = grossDiscount.add(c.getAdjustmentValue().multiply(BigDecimal.valueOf(c.getCurrentRedemptions())));
                } else {
                    // Estimated nominal basket of €450 for percentage coupons
                    BigDecimal nominalBasket = BigDecimal.valueOf(450);
                    BigDecimal perRedemption = nominalBasket.multiply(c.getAdjustmentValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    grossDiscount = grossDiscount.add(perRedemption.multiply(BigDecimal.valueOf(c.getCurrentRedemptions())));
                }
            }
        }

        double redemptionRate = coupons.isEmpty() ? 0.0 :
                ((double) coupons.stream().filter(c -> c.getCurrentRedemptions() > 0).count() / coupons.size()) * 100.0;

        String topCampaign = coupons.stream()
                .max(java.util.Comparator.comparingInt(Coupon::getCurrentRedemptions))
                .map(Coupon::getCode)
                .orElse("NONE");

        return new com.luna.aggarly.pricing.dto.CouponMetricsResponse(
                grossDiscount,
                activeCount,
                Math.round(redemptionRate * 10.0) / 10.0,
                topCampaign
        );
    }
}
