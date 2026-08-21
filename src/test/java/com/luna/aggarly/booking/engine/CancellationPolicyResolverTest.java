package com.luna.aggarly.booking.engine;

import com.luna.aggarly.booking.engine.policy.FlexiblePolicyCalculator;
import com.luna.aggarly.booking.engine.policy.ModeratePolicyCalculator;
import com.luna.aggarly.booking.engine.policy.NonRefundablePolicyCalculator;
import com.luna.aggarly.booking.engine.policy.StrictPolicyCalculator;
import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellationPolicyResolverTest {

    private CancellationPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        List<CancellationPolicyCalculator> calculators = List.of(
                new FlexiblePolicyCalculator(),
                new ModeratePolicyCalculator(),
                new StrictPolicyCalculator(),
                new NonRefundablePolicyCalculator()
        );
        resolver = new CancellationPolicyResolver(calculators);
    }

    @Test
    void testFlexiblePolicy() {
        LocalDate today = LocalDate.now();
        assertEquals(BigDecimal.valueOf(100), resolver.resolveRefundPercentage(CancellationPolicyType.FLEXIBLE, today.plusDays(1), today));
        assertEquals(BigDecimal.ZERO, resolver.resolveRefundPercentage(CancellationPolicyType.FLEXIBLE, today, today));
    }

    @Test
    void testModeratePolicy() {
        LocalDate today = LocalDate.now();
        assertEquals(BigDecimal.valueOf(100), resolver.resolveRefundPercentage(CancellationPolicyType.MODERATE, today.plusDays(5), today));
        assertEquals(BigDecimal.valueOf(50), resolver.resolveRefundPercentage(CancellationPolicyType.MODERATE, today.plusDays(3), today));
        assertEquals(BigDecimal.ZERO, resolver.resolveRefundPercentage(CancellationPolicyType.MODERATE, today, today));
    }

    @Test
    void testStrictPolicy() {
        LocalDate today = LocalDate.now();
        assertEquals(BigDecimal.valueOf(50), resolver.resolveRefundPercentage(CancellationPolicyType.STRICT, today.plusDays(14), today));
        assertEquals(BigDecimal.ZERO, resolver.resolveRefundPercentage(CancellationPolicyType.STRICT, today.plusDays(13), today));
    }

    @Test
    void testNonRefundablePolicy() {
        LocalDate today = LocalDate.now();
        assertEquals(BigDecimal.ZERO, resolver.resolveRefundPercentage(CancellationPolicyType.NON_REFUNDABLE, today.plusDays(30), today));
    }
}
