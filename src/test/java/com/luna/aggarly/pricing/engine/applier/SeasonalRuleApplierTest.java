package com.luna.aggarly.pricing.engine.applier;

import com.luna.aggarly.pricing.engine.RuleApplicationResult;
import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SeasonalRuleApplierTest {

    private final SeasonalRuleApplier applier = new SeasonalRuleApplier();

    @Test
    void appliesWithinDateRange() {
        PricingRule rule = PricingRule.builder()
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 9, 1))
                .adjustmentType(AdjustmentType.PERCENTAGE)
                .adjustmentValue(BigDecimal.valueOf(20)) // +20%
                .build();

        LocalDate inSeason = LocalDate.of(2026, 7, 15);
        RuleApplicationResult result = applier.apply(BigDecimal.valueOf(100), inSeason, rule);

        assertTrue(result.isApplied());
        assertEquals(0, BigDecimal.valueOf(120).compareTo(result.getAdjustedPrice()));
    }

    @Test
    void excludesBoundaryEndNight() {
        // Half-open interval: [startDate, endDate)
        PricingRule rule = PricingRule.builder()
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 9, 1))
                .adjustmentType(AdjustmentType.FIXED_AMOUNT)
                .adjustmentValue(BigDecimal.valueOf(50)) // +$50
                .build();

        LocalDate endNight = LocalDate.of(2026, 9, 1);
        RuleApplicationResult result = applier.apply(BigDecimal.valueOf(100), endNight, rule);

        assertFalse(result.isApplied());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.getAdjustedPrice()));
    }
}
