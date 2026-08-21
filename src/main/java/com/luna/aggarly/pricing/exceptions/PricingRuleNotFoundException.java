package com.luna.aggarly.pricing.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PricingRuleNotFoundException extends AggarlyException {
    public PricingRuleNotFoundException(UUID id) {
        super("Pricing rule not found: " + id, HttpStatus.NOT_FOUND, "PRICING_RULE_NOT_FOUND");
    }
}
