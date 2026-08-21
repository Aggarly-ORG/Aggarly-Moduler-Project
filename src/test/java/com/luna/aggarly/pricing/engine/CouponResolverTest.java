package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.Coupon;
import com.luna.aggarly.pricing.exceptions.CouponAlreadyRedeemedException;
import com.luna.aggarly.pricing.exceptions.CouponExpiredException;
import com.luna.aggarly.pricing.exceptions.InvalidCouponException;
import com.luna.aggarly.pricing.repository.CouponRedemptionRepository;
import com.luna.aggarly.pricing.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponResolverTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponRedemptionRepository redemptionRepository;

    @InjectMocks
    private CouponResolver resolver;

    @Test
    void resolvesValidCoupon() {
        UUID userId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .code("SUMMER10")
                .adjustmentType(AdjustmentType.PERCENTAGE)
                .adjustmentValue(BigDecimal.valueOf(10))
                .active(true)
                .build();

        when(couponRepository.findByCodeAndActiveTrue("SUMMER10")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), userId)).thenReturn(false);

        RuleApplicationResult result = resolver.resolveAndApply("SUMMER10", BigDecimal.valueOf(100), userId);

        assertTrue(result.isApplied());
        assertEquals(0, BigDecimal.valueOf(90).compareTo(result.getAdjustedPrice()));
    }

    @Test
    void throwsWhenExpired() {
        Coupon coupon = Coupon.builder()
                .code("OLD")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .active(true)
                .build();

        when(couponRepository.findByCodeAndActiveTrue("OLD")).thenReturn(Optional.of(coupon));

        assertThrows(CouponExpiredException.class, () -> 
                resolver.resolveAndApply("OLD", BigDecimal.valueOf(100), UUID.randomUUID()));
    }

    @Test
    void throwsWhenMaxRedemptionsReached() {
        Coupon coupon = Coupon.builder()
                .code("LIMIT")
                .maxRedemptions(5)
                .currentRedemptions(5)
                .active(true)
                .build();

        when(couponRepository.findByCodeAndActiveTrue("LIMIT")).thenReturn(Optional.of(coupon));

        assertThrows(InvalidCouponException.class, () -> 
                resolver.resolveAndApply("LIMIT", BigDecimal.valueOf(100), UUID.randomUUID()));
    }

    @Test
    void throwsWhenAlreadyRedeemedByUser() {
        UUID userId = UUID.randomUUID();
        Coupon coupon = Coupon.builder()
                .code("ONCE")
                .active(true)
                .build();
        coupon.setId(UUID.randomUUID());

        when(couponRepository.findByCodeAndActiveTrue("ONCE")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), userId)).thenReturn(true);

        assertThrows(CouponAlreadyRedeemedException.class, () -> 
                resolver.resolveAndApply("ONCE", BigDecimal.valueOf(100), userId));
    }
}
