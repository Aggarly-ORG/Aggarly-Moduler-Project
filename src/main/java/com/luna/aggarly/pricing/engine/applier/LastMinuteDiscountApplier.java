package com.luna.aggarly.pricing.engine.applier;

import com.luna.aggarly.pricing.engine.RuleApplicationResult;
import com.luna.aggarly.pricing.engine.SubtotalRuleApplier;
import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class LastMinuteDiscountApplier implements SubtotalRuleApplier {

    @Override
    public PricingRuleType supports() { return PricingRuleType.LAST_MINUTE_DISCOUNT; }

    @Override
    public RuleApplicationResult apply(BigDecimal subtotal, LocalDate checkIn, LocalDate checkOut, PricingRule rule) {
        long daysInAdvance = ChronoUnit.DAYS.between(LocalDate.now(), checkIn);
        // Only apply if they book within 'thresholdValue' days of check-in
        if (daysInAdvance > rule.getThresholdValue()) {
            return RuleApplicationResult.builder().adjustedPrice(subtotal).applied(false).build();
        }

        BigDecimal adjusted = rule.getAdjustmentType() == AdjustmentType.PERCENTAGE
                ? subtotal.subtract(subtotal.multiply(rule.getAdjustmentValue()).divide(BigDecimal.valueOf(100)))
                : subtotal.subtract(rule.getAdjustmentValue());

        return RuleApplicationResult.builder()
                .adjustedPrice(adjusted)
                .applied(true)
                .description("Last minute discount (−" + rule.getAdjustmentValue()
                        + (rule.getAdjustmentType() == AdjustmentType.PERCENTAGE ? "%)" : ")"))
                .build();
    }
}
