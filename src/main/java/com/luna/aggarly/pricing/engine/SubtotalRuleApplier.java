package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SubtotalRuleApplier {
    PricingRuleType supports();
    RuleApplicationResult apply(BigDecimal subtotal, LocalDate checkIn, LocalDate checkOut, PricingRule rule);
}
