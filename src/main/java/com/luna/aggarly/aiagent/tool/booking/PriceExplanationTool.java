package com.luna.aggarly.aiagent.tool.booking;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.pricing.dto.PriceLineItem;
import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceExplanationTool implements Tool<PriceExplanationTool.Params, PriceExplanationTool.Response> {

    public record Params(
            @NotNull(message = "Property ID is required")
            @JsonPropertyDescription("The UUID of the property to calculate price for.")
            UUID propertyId,

            @JsonPropertyDescription("Check-in date in YYYY-MM-DD format (defaults to tomorrow if omitted).")
            String checkIn,

            @JsonPropertyDescription("Check-out date in YYYY-MM-DD format (defaults to 3 days from tomorrow).")
            String checkOut,

            @JsonPropertyDescription("Optional promotional coupon code to apply.")
            String couponCode
    ) {}

    public record Response(
            UUID propertyId,
            double basePricePerNight,
            int totalNights,
            double totalBasePrice,
            double cleaningFee,
            double serviceFee,
            double taxAmount,
            double discountAmount,
            double totalAmount,
            String currency,
            String breakdownSummary
    ) {}

    private final PricingRuleService pricingRuleService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "booking.priceExplanation";
    }

    @Override
    public String description() {
        return "Calculate a complete pricing breakdown for a stay including base rate per night, total nights, cleaning fee, service fee, taxes, coupon discounts, and grand total.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        LocalDate checkInDate = params.checkIn() != null ? LocalDate.parse(params.checkIn()) : LocalDate.now().plusDays(1);
        LocalDate checkOutDate = params.checkOut() != null ? LocalDate.parse(params.checkOut()) : LocalDate.now().plusDays(3);

        PriceQuoteResponse quote = pricingRuleService.getQuote(
                params.propertyId(),
                checkInDate,
                checkOutDate,
                params.couponCode(),
                userId
        );

        double total = quote.total() != null ? quote.total().doubleValue() : 0.0;
        double totalBase = quote.basePrice() != null ? quote.basePrice().doubleValue() : total;
        int nights = Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate));
        double baseRate = nights > 0 ? (totalBase / nights) : totalBase;

        double cleaning = 0.0;
        double service = 0.0;
        double taxes = 0.0;
        double discount = 0.0;
        if (quote.lineItems() != null) {
            for (PriceLineItem item : quote.lineItems()) {
                if (item.label() != null) {
                    String lower = item.label().toLowerCase();
                    double amt = item.amount() != null ? item.amount().doubleValue() : 0.0;
                    if (lower.contains("clean")) cleaning += amt;
                    else if (lower.contains("service")) service += amt;
                    else if (lower.contains("tax")) taxes += amt;
                    else if (lower.contains("discount") || lower.contains("coupon")) discount += Math.abs(amt);
                }
            }
        }

        Response resp = new Response(
                params.propertyId(),
                baseRate,
                nights,
                totalBase,
                cleaning,
                service,
                taxes,
                discount,
                total,
                "USD",
                "Standard nightly pricing breakdown"
        );
        return ToolResult.ok(resp);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Response.class);
    }
}
