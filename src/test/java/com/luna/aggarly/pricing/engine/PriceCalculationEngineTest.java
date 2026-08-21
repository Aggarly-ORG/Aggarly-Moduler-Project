package com.luna.aggarly.pricing.engine;

import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.engine.applier.SeasonalRuleApplier;
import com.luna.aggarly.pricing.engine.applier.WeekendRuleApplier;
import com.luna.aggarly.pricing.entity.AdjustmentType;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import com.luna.aggarly.pricing.repository.PricingRuleRepository;
import com.luna.aggarly.pricing.repository.TaxRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceCalculationEngineTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;
    @Mock
    private TaxRuleRepository taxRuleRepository;
    @Mock
    private CouponResolver couponResolver;

    private PriceCalculationEngine engine;

    @BeforeEach
    void setUp() {
        List<RuleApplier> nightlyAppliers = List.of(new SeasonalRuleApplier(), new WeekendRuleApplier());
        List<SubtotalRuleApplier> subtotalAppliers = List.of(); // empty for this test
        engine = new PriceCalculationEngine(pricingRuleRepository, taxRuleRepository, nightlyAppliers, subtotalAppliers, couponResolver);
    }

    @Test
    void compoundsNightlyRulesInOrder() {
        UUID propId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2026, 8, 14); // Friday
        LocalDate checkOut = LocalDate.of(2026, 8, 15); // Saturday, 1 night

        PricingRule season = PricingRule.builder()
                .type(PricingRuleType.SEASONAL)
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 9, 1))
                .adjustmentType(AdjustmentType.PERCENTAGE)
                .adjustmentValue(BigDecimal.valueOf(10))
                .priority(0)
                .build();

        // Rule 2: Weekend +$20 (applies second)
        PricingRule weekend = PricingRule.builder()
                .type(PricingRuleType.WEEKEND)
                .adjustmentType(AdjustmentType.FIXED_AMOUNT)
                .adjustmentValue(BigDecimal.valueOf(20))
                .priority(1)
                .build();

        when(pricingRuleRepository.findByPropertyIdAndActiveTrueOrderByPriorityAsc(propId))
                .thenReturn(List.of(season, weekend));
        when(taxRuleRepository.findByRegionAndActiveTrue(any())).thenReturn(Optional.empty());

        // Base 100 -> Seasonal +10% = 110 -> Weekend +$20 = 130
        PriceQuoteResponse response = engine.calculate(propId, BigDecimal.valueOf(100), checkIn, checkOut, "US", null, null);

        // Subtotal = 130. Fees = 25 (service) + 30 (cleaning) = 55. Total = 185
        assertEquals(0, BigDecimal.valueOf(185).compareTo(response.total()));
    }
}
