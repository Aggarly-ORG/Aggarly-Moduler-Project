package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.Coupon;
import com.luna.aggarly.pricing.exceptions.CouponAlreadyRedeemedException;
import com.luna.aggarly.pricing.exceptions.CouponExpiredException;
import com.luna.aggarly.pricing.exceptions.InvalidCouponException;
import com.luna.aggarly.pricing.repository.CouponRedemptionRepository;
import com.luna.aggarly.pricing.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponResolver {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public RuleApplicationResult resolveAndApply(String code, BigDecimal subtotal, UUID userId) {
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(code.toUpperCase())
                .orElseThrow(() -> new InvalidCouponException("Invalid coupon code: " + code));

        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(Instant.now())) {
            throw new CouponExpiredException(code);
        }
        if (coupon.getMaxRedemptions() != null && coupon.getCurrentRedemptions() >= coupon.getMaxRedemptions()) {
            throw new InvalidCouponException("Coupon has reached its redemption limit: " + code);
        }
        if (coupon.getMinSubtotal() != null && subtotal.compareTo(coupon.getMinSubtotal()) < 0) {
            throw new InvalidCouponException("Subtotal does not meet minimum for this coupon: " + code);
        }
        if (redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new CouponAlreadyRedeemedException(code);
        }

        BigDecimal adjusted = coupon.getAdjustmentType() == AdjustmentType.PERCENTAGE
                ? subtotal.subtract(subtotal.multiply(coupon.getAdjustmentValue()).divide(BigDecimal.valueOf(100)))
                : subtotal.subtract(coupon.getAdjustmentValue());

        return RuleApplicationResult.builder()
                .adjustedPrice(adjusted)
                .applied(true)
                .description("Coupon " + code + " applied (−" + coupon.getAdjustmentValue()
                        + (coupon.getAdjustmentType() == AdjustmentType.PERCENTAGE ? "%)" : ")"))
                .build();
    }
}
