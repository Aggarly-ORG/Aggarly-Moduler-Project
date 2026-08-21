package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PricingRuleResponse(UUID id, UUID propertyId, String type, LocalDate startDate, LocalDate endDate,
                                   String adjustmentType, BigDecimal adjustmentValue,
                                   Integer thresholdValue, int priority, boolean active) {}
