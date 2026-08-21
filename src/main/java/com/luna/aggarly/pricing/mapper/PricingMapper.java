package com.luna.aggarly.pricing.mapper;

import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.dto.PricingRuleRequest;
import com.luna.aggarly.pricing.dto.PricingRuleResponse;
import com.luna.aggarly.pricing.entity.Coupon;
import com.luna.aggarly.pricing.entity.PricingRule;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface PricingMapper {

    @Mapping(target = "active", constant = "true")
    PricingRule toEntity(UUID propertyId, PricingRuleRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget PricingRule rule, PricingRuleRequest request);

    PricingRuleResponse toResponse(PricingRule rule);

    @Mapping(target = "currentRedemptions", constant = "0")
    @Mapping(target = "active", constant = "true")
    Coupon toEntity(CouponRequest request);

    CouponResponse toResponse(Coupon coupon);
}