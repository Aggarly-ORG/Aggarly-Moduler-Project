package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.booking.record.PriceExplanationParams;
import com.luna.aggarly.aiagent.tool.booking.record.PriceExplanationToolResponse;
import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PriceExplanationTool implements Tool<PriceExplanationParams, PriceExplanationToolResponse> {

    private final PricingRuleService pricingRuleService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.priceExplanation";
    }

    @Override
    public String description() {
        return "Get a complete pricing breakdown, including base rate, discounts, taxes, and service fees.";
    }

    @Override
    public Class<PriceExplanationParams> parameterType() {
        return PriceExplanationParams.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ToolResult<PriceExplanationToolResponse> execute(PriceExplanationParams params, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        PriceQuoteResponse quote = pricingRuleService.getQuote(
                params.propertyId(),
                params.checkIn() != null ? LocalDate.parse(params.checkIn()) : LocalDate.now().plusDays(1),
                params.checkOut() != null ? LocalDate.parse(params.checkOut()) : LocalDate.now().plusDays(3),
                params.couponCode(),
                userId
        );

        java.util.List<PriceExplanationToolResponse.PriceBreakdownItemDto> items;
        if (quote.lineItems() != null && !quote.lineItems().isEmpty()) {
            items = quote.lineItems().stream()
                    .map(li -> new PriceExplanationToolResponse.PriceBreakdownItemDto(
                            li.label(),
                            li.amount() != null ? li.amount().doubleValue() : 0.0))
                    .toList();
        } else {
            items = java.util.List.of(
                    new PriceExplanationToolResponse.PriceBreakdownItemDto("Base Stay Rate", quote.basePrice() != null ? quote.basePrice().doubleValue() : 0.0)
            );
        }

        double totalAmount = quote.total() != null ? quote.total().doubleValue() : (quote.basePrice() != null ? quote.basePrice().doubleValue() : 0.0);
        double baseRate = quote.basePrice() != null ? quote.basePrice().doubleValue() : totalAmount;

        PriceExplanationToolResponse resp = new PriceExplanationToolResponse(
                params.propertyId(),
                "Price Breakdown & Fees",
                "€",
                baseRate,
                0.0,
                totalAmount,
                totalAmount,
                items
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PriceExplanationParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PriceExplanationToolResponse.class);
    }
}

