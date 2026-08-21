package com.luna.aggarly.pricing.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller providing dynamic price breakdown quotes including seasonal adjustments and coupons.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/quote")
@RequiredArgsConstructor
@Tag(name = "Price Quote", description = "Dynamic Price Calculation & Breakdown APIs")
public class PriceQuoteController {

    private final PricingRuleService pricingRuleService;

    @GetMapping
    @Operation(summary = "Get dynamic price quote for date range (Public/Authenticated)")
    public ResponseEntity<ApiResponse<PriceQuoteResponse>> getQuote(
            @PathVariable UUID propertyId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut,
            @RequestParam(required = false) String couponCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = (principal != null) ? principal.getUserId() : null;
        PriceQuoteResponse quote = pricingRuleService.getQuote(propertyId, checkIn, checkOut, couponCode, userId);
        return ApiResponse.ok(quote, "Price quote calculated successfully").toResponseEntity();
    }
}
