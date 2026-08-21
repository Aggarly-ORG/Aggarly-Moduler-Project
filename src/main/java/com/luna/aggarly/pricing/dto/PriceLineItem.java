package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;

public record PriceLineItem(String label, BigDecimal amount) {}
