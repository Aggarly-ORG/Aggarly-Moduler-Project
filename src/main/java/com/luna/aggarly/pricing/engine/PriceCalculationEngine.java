package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.dto.PriceLineItem;
import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import com.luna.aggarly.pricing.repository.PricingRuleRepository;
import com.luna.aggarly.pricing.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PriceCalculationEngine {

    private static final BigDecimal SERVICE_FEE_AMOUNT = BigDecimal.valueOf(25);
    private static final BigDecimal CLEANING_FEE_AMOUNT = BigDecimal.valueOf(30);

    private final PricingRuleRepository pricingRuleRepository;
    private final TaxRuleRepository taxRuleRepository;
    private final List<RuleApplier> nightlyAppliers;
    private final List<SubtotalRuleApplier> subtotalAppliers;
    private final CouponResolver couponResolver;

    public PriceQuoteResponse calculate(UUID propertyId, BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut,
                                        String region, String couponCode, UUID userId) {

        Map<PricingRuleType, List<PricingRule>> rulesByType = loadRulesByType(propertyId);
        List<PriceLineItem> lineItems = new ArrayList<>();

        BigDecimal subtotal = applyNightlyRules(basePrice, checkIn, checkOut, rulesByType, lineItems);
        subtotal = applySubtotalRules(subtotal, checkIn, checkOut, rulesByType, lineItems);
        subtotal = applyCoupon(subtotal, couponCode, userId, lineItems);

        BigDecimal afterFees = applyFees(subtotal, lineItems);
        BigDecimal total = applyTax(afterFees, region, lineItems);

        return new PriceQuoteResponse(propertyId, checkIn, checkOut, basePrice, lineItems, total);
    }

    private Map<PricingRuleType, List<PricingRule>> loadRulesByType(UUID propertyId) {
        List<PricingRule> allRules = pricingRuleRepository.findByPropertyIdAndActiveTrueOrderByPriorityAsc(propertyId);
        return allRules.stream().collect(Collectors.groupingBy(PricingRule::getType));
    }

    private BigDecimal applyNightlyRules(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut,
                                         Map<PricingRuleType, List<PricingRule>> rulesByType,
                                         List<PriceLineItem> lineItems) {

        BigDecimal subtotal = BigDecimal.ZERO;

        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            BigDecimal nightlyPrice = basePrice;

            for (RuleApplier applier : nightlyAppliers) {
                for (PricingRule rule : rulesByType.getOrDefault(applier.supports(), List.of())) {
                    var result = applier.apply(nightlyPrice, night, rule);
                    if (result.isApplied()) {
                        BigDecimal delta = result.getAdjustedPrice().subtract(nightlyPrice);
                        lineItems.add(new PriceLineItem(night + ": " + result.getDescription(), delta));
                        nightlyPrice = result.getAdjustedPrice();
                    }
                }
            }

            subtotal = subtotal.add(nightlyPrice);
        }

        return subtotal;
    }

    private BigDecimal applySubtotalRules(BigDecimal subtotal, LocalDate checkIn, LocalDate checkOut,
                                          Map<PricingRuleType, List<PricingRule>> rulesByType,
                                          List<PriceLineItem> lineItems) {

        BigDecimal running = subtotal;

        for (SubtotalRuleApplier applier : subtotalAppliers) {
            for (PricingRule rule : rulesByType.getOrDefault(applier.supports(), List.of())) {
                var result = applier.apply(running, checkIn, checkOut, rule);
                if (result.isApplied()) {
                    lineItems.add(new PriceLineItem(result.getDescription(), result.getAdjustedPrice().subtract(running)));
                    running = result.getAdjustedPrice();
                }
            }
        }

        return running;
    }

    private BigDecimal applyCoupon(BigDecimal subtotal, String couponCode, UUID userId, List<PriceLineItem> lineItems) {

        if (couponCode == null || couponCode.isBlank()) {
            return subtotal;
        }

        var result = couponResolver.resolveAndApply(couponCode, subtotal, userId);
        lineItems.add(new PriceLineItem(result.getDescription(), result.getAdjustedPrice().subtract(subtotal)));
        return result.getAdjustedPrice();
    }

    private BigDecimal applyFees(BigDecimal subtotal, List<PriceLineItem> lineItems) {
        lineItems.add(new PriceLineItem("Service fee", SERVICE_FEE_AMOUNT));
        lineItems.add(new PriceLineItem("Cleaning fee", CLEANING_FEE_AMOUNT));
        return subtotal.add(SERVICE_FEE_AMOUNT).add(CLEANING_FEE_AMOUNT);
    }

    private BigDecimal applyTax(BigDecimal afterFees, String region, List<PriceLineItem> lineItems) {
        BigDecimal taxRate = taxRuleRepository.findByRegionAndActiveTrue(region)
                .map(taxRule -> taxRule.getRatePercentage())
                .orElse(BigDecimal.ZERO);

        BigDecimal tax = afterFees.multiply(taxRate).divide(BigDecimal.valueOf(100));
        lineItems.add(new PriceLineItem("Tax (" + taxRate + "%)", tax));

        return afterFees.add(tax);
    }
}