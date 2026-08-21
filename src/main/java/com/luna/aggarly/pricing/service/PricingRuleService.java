package com.luna.aggarly.pricing.service;

import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.dto.PricingRuleRequest;
import com.luna.aggarly.pricing.dto.PricingRuleResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PricingRuleService {
    List<PricingRuleResponse> getRulesForProperty(UUID propertyId);
    PricingRuleResponse createRule(UUID propertyId, PricingRuleRequest request);
    PricingRuleResponse updateRule(UUID propertyId, UUID ruleId, PricingRuleRequest request);
    void deleteRule(UUID propertyId, UUID ruleId);
    PriceQuoteResponse getQuote(UUID propertyId, LocalDate checkIn, LocalDate checkOut, String couponCode, UUID userId);
}
