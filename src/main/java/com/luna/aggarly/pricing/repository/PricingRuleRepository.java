package com.luna.aggarly.pricing.repository;

import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.entity.PricingRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    List<PricingRule> findByPropertyIdAndActiveTrueOrderByPriorityAsc(UUID propertyId);

    List<PricingRule> findByPropertyIdAndTypeAndActiveTrue(UUID propertyId, PricingRuleType type);

    List<PricingRule> findByPropertyIdAndTypeAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThan(
            UUID propertyId, PricingRuleType type, LocalDate stayEnd, LocalDate stayStart);
}
