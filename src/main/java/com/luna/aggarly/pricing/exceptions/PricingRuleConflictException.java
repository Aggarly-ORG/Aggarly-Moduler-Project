package com.luna.aggarly.pricing.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class PricingRuleConflictException extends AggarlyException {
    public PricingRuleConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "PRICING_RULE_CONFLICT");
    }
}
