package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PriceQuoteRequest(UUID propertyId, LocalDate checkIn, LocalDate checkOut, String couponCode) {}
