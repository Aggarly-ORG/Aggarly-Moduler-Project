package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricingRuleRequest(String type, LocalDate startDate, LocalDate endDate,
                                  String adjustmentType, BigDecimal adjustmentValue,
                                  Integer thresholdValue, Integer priority) {}
