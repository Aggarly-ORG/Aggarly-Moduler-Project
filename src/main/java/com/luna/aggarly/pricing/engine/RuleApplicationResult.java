package com.luna.aggarly.pricing.engine;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RuleApplicationResult {
    private BigDecimal adjustedPrice;
    private boolean applied;
    private String description;
}
