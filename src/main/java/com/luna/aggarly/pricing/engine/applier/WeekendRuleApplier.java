package com.luna.aggarly.pricing.engine.applier;

import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import com.luna.aggarly.pricing.engine.RuleApplicationResult;
import com.luna.aggarly.pricing.engine.RuleApplier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Component
public class WeekendRuleApplier implements RuleApplier {

    @Override
    public PricingRuleType supports() { return PricingRuleType.WEEKEND; }

    @Override
    public RuleApplicationResult apply(BigDecimal runningNightlyPrice, LocalDate night, PricingRule rule) {
        boolean isWeekendNight = night.getDayOfWeek() == DayOfWeek.FRIDAY
                || night.getDayOfWeek() == DayOfWeek.SATURDAY;

        if (!isWeekendNight) {
            return RuleApplicationResult.builder().adjustedPrice(runningNightlyPrice).applied(false).build();
        }

        BigDecimal adjusted = applyAdjustment(runningNightlyPrice, rule);
        return RuleApplicationResult.builder()
                .adjustedPrice(adjusted)
                .applied(true)
                .description(describeAdjustment(rule, "Weekend surcharge"))
                .build();
    }

    private BigDecimal applyAdjustment(BigDecimal price, PricingRule rule) {
        return rule.getAdjustmentType() == AdjustmentType.PERCENTAGE
                ? price.add(price.multiply(rule.getAdjustmentValue()).divide(BigDecimal.valueOf(100)))
                : price.add(rule.getAdjustmentValue());
    }

    private String describeAdjustment(PricingRule rule, String label) {
        return rule.getAdjustmentType() == AdjustmentType.PERCENTAGE
                ? label + " (+" + rule.getAdjustmentValue() + "%)"
                : label + " (+$" + rule.getAdjustmentValue() + ")";
    }
}
