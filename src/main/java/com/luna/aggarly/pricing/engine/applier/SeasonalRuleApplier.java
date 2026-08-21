package com.luna.aggarly.pricing.engine.applier;

import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import com.luna.aggarly.pricing.engine.RuleApplicationResult;
import com.luna.aggarly.pricing.engine.RuleApplier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class SeasonalRuleApplier implements RuleApplier {

    @Override
    public PricingRuleType supports() { return PricingRuleType.SEASONAL; }

    @Override
    public RuleApplicationResult apply(BigDecimal runningNightlyPrice, LocalDate night, PricingRule rule) {
        boolean inSeason = !night.isBefore(rule.getStartDate()) && night.isBefore(rule.getEndDate());
        if (!inSeason) {
            return RuleApplicationResult.builder().adjustedPrice(runningNightlyPrice).applied(false).build();
        }

        BigDecimal adjusted = rule.getAdjustmentType() == AdjustmentType.PERCENTAGE
                ? runningNightlyPrice.add(runningNightlyPrice.multiply(rule.getAdjustmentValue()).divide(BigDecimal.valueOf(100)))
                : runningNightlyPrice.add(rule.getAdjustmentValue());

        return RuleApplicationResult.builder()
                .adjustedPrice(adjusted)
                .applied(true)
                .description("Seasonal adjustment (" + rule.getStartDate() + " – " + rule.getEndDate() + ")")
                .build();
    }
}
