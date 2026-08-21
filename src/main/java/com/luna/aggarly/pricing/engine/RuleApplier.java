package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface RuleApplier {

    PricingRuleType supports();

    /**
     * Given the running per-night price and the specific night being priced,
     * return the adjusted price plus a human-readable description of what changed
     * (or empty description if the rule didn't apply to this particular night).
     */
    RuleApplicationResult apply(BigDecimal runningNightlyPrice, LocalDate night, PricingRule rule);
}
