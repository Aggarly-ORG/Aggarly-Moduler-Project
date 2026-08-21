package com.luna.aggarly.aiagent.tool.booking.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.UUID;

public record PriceExplanationToolResponse(
        @JsonPropertyDescription("The unique identifier of the property.")
        UUID propertyId,

        @JsonPropertyDescription("Title for the price card.")
        String title,

        @JsonPropertyDescription("Currency symbol (e.g. € or $).")
        String currency,

        @JsonPropertyDescription("Base nightly price sum.")
        double basePrice,

        @JsonPropertyDescription("Applied discount total amount.")
        double discountAmount,

        @JsonPropertyDescription("Total calculated price.")
        double total,

        @JsonPropertyDescription("Total calculated price alias.")
        double totalPrice,

        @JsonPropertyDescription("Detailed line items of the price breakdown.")
        List<PriceBreakdownItemDto> items
) {
    public record PriceBreakdownItemDto(String label, double amount) {}
}
