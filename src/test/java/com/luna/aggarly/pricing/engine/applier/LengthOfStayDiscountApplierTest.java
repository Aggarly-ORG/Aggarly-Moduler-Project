package com.luna.aggarly.pricing.engine.applier;

import com.luna.aggarly.pricing.engine.RuleApplicationResult;
import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LengthOfStayDiscountApplierTest {

    private final LengthOfStayDiscountApplier applier = new LengthOfStayDiscountApplier();

    @Test
    void appliesAtExactThreshold() {
        PricingRule rule = PricingRule.builder()
                .thresholdValue(7) // 7 nights
                .adjustmentType(AdjustmentType.PERCENTAGE)
                .adjustmentValue(BigDecimal.valueOf(10)) // -10%
                .build();

        LocalDate checkIn = LocalDate.of(2026, 1, 1);
        LocalDate checkOut = LocalDate.of(2026, 1, 8); // exactly 7 nights

        RuleApplicationResult result = applier.apply(BigDecimal.valueOf(700), checkIn, checkOut, rule);

        assertTrue(result.isApplied());
        assertEquals(0, BigDecimal.valueOf(630).compareTo(result.getAdjustedPrice())); // 700 - 10% = 630
    }

    @Test
    void skipsBelowThreshold() {
        PricingRule rule = PricingRule.builder()
                .thresholdValue(7)
                .adjustmentType(AdjustmentType.FIXED_AMOUNT)
                .adjustmentValue(BigDecimal.valueOf(50))
                .build();

        LocalDate checkIn = LocalDate.of(2026, 1, 1);
        LocalDate checkOut = LocalDate.of(2026, 1, 7); // 6 nights

        RuleApplicationResult result = applier.apply(BigDecimal.valueOf(600), checkIn, checkOut, rule);

        assertFalse(result.isApplied());
        assertEquals(0, BigDecimal.valueOf(600).compareTo(result.getAdjustedPrice()));
    }
}
