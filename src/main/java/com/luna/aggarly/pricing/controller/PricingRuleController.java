package com.luna.aggarly.pricing.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.pricing.dto.PricingRuleRequest;
import com.luna.aggarly.pricing.dto.PricingRuleResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/pricing-rules")
@RequiredArgsConstructor
@Tag(name = "Pricing Rules", description = "Host Custom Pricing & Seasonality Rules APIs")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    @GetMapping
    @Operation(summary = "Get pricing rules for property (Public)")
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getRules(@PathVariable UUID propertyId) {
        List<PricingRuleResponse> rules = pricingRuleService.getRulesForProperty(propertyId);
        return ApiResponse.ok(rules, "Pricing rules retrieved successfully").toResponseEntity();
    }

    @PostMapping
    @PreAuthorize("hasRole('HOST')")
    @Operation(summary = "Create custom pricing rule (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createRule(
            @PathVariable UUID propertyId, @Valid @RequestBody PricingRuleRequest request) {
        PricingRuleResponse response = pricingRuleService.createRule(propertyId, request);
        return ApiResponse.created(response, "Pricing rule created successfully").toResponseEntity();
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('HOST')")
    @Operation(summary = "Update custom pricing rule (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PricingRuleResponse>> updateRule(
            @PathVariable UUID propertyId, @PathVariable UUID ruleId,
            @Valid @RequestBody PricingRuleRequest request) {
        PricingRuleResponse response = pricingRuleService.updateRule(propertyId, ruleId, request);
        return ApiResponse.ok(response, "Pricing rule updated successfully").toResponseEntity();
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('HOST')")
    @Operation(summary = "Delete custom pricing rule (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID propertyId, @PathVariable UUID ruleId) {
        pricingRuleService.deleteRule(propertyId, ruleId);
        return ApiResponse.<Void>empty("Pricing rule deleted successfully").toResponseEntity();
    }
}
