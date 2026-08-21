package com.luna.aggarly.pricing.service.impl;

import com.luna.aggarly.pricing.dto.*;
import com.luna.aggarly.pricing.engine.PriceCalculationEngine;
import com.luna.aggarly.pricing.entity.PricingRule;
import com.luna.aggarly.pricing.exceptions.PricingRuleNotFoundException;
import com.luna.aggarly.pricing.mapper.PricingMapper;
import com.luna.aggarly.pricing.repository.PricingRuleRepository;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final PriceCalculationEngine calculationEngine;
    private final PricingMapper mapper;
    private final PropertyService propertyService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    public List<PricingRuleResponse> getRulesForProperty(UUID propertyId) {
        return pricingRuleRepository.findByPropertyIdAndActiveTrueOrderByPriorityAsc(propertyId)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PricingRuleResponse createRule(UUID propertyId, PricingRuleRequest request) {
        PricingRule rule = mapper.toEntity(propertyId, request);
        PricingRule saved = pricingRuleRepository.save(rule);
        eventPublisher.publishEvent(new com.luna.aggarly.pricing.event.PricingRuleChangedEvent(this, propertyId));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PricingRuleResponse updateRule(UUID propertyId, UUID ruleId, PricingRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PricingRuleNotFoundException(ruleId));
        mapper.updateEntity(rule, request);
        PricingRule saved = pricingRuleRepository.save(rule);
        eventPublisher.publishEvent(new com.luna.aggarly.pricing.event.PricingRuleChangedEvent(this, propertyId));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteRule(UUID propertyId, UUID ruleId) {
        PricingRule rule = pricingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new PricingRuleNotFoundException(ruleId));
        pricingRuleRepository.delete(rule); // soft-deleted via @SQLDelete
        eventPublisher.publishEvent(new com.luna.aggarly.pricing.event.PricingRuleChangedEvent(this, propertyId));
    }

    @Override
    public PriceQuoteResponse getQuote(UUID propertyId, LocalDate checkIn, LocalDate checkOut,
                                        String couponCode, UUID userId) {
        PropertyResponse property = propertyService.getPropertyById(propertyId);
        return calculationEngine.calculate(propertyId, property.basePricePerNight(), checkIn, checkOut,
                property.address().country(), couponCode, userId);
    }
}
