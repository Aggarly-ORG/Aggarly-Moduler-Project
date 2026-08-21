package com.luna.aggarly.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceQuoteResponse(UUID propertyId, LocalDate checkIn, LocalDate checkOut,
                                  BigDecimal basePrice, List<PriceLineItem> lineItems, BigDecimal total) {}
