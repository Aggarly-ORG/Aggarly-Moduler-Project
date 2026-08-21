package com.luna.aggarly.pricing.repository;

import com.luna.aggarly.pricing.entity.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {
    Optional<TaxRule> findByRegionAndActiveTrue(String region);
}
