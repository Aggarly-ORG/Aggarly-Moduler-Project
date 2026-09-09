package com.luna.aggarly.pricing.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.pricing.dto.CouponRequest;
import com.luna.aggarly.pricing.dto.CouponResponse;
import com.luna.aggarly.pricing.dto.CouponValidationResponse;
import com.luna.aggarly.pricing.dto.ValidateCouponRequest;
import com.luna.aggarly.pricing.service.CouponService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing promotional discount coupons, admin creation, and checkout validation.
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Promotional Codes & Discount Validation APIs")
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new coupon code (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        CouponResponse response = couponService.createCoupon(request);
        return ApiResponse.created(response, "Coupon created successfully").toResponseEntity();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all coupons with optional tier and seasonal filtering (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getAllCoupons(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String tier,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean seasonal,
            Pageable pageable) {
        Page<CouponResponse> page = couponService.getAllCoupons(tier, seasonal, pageable);
        return ApiResponse.paged(page, "Coupons retrieved successfully").toResponseEntity();
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get aggregated coupon campaign metrics (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<com.luna.aggarly.pricing.dto.CouponMetricsResponse>> getCouponMetrics() {
        com.luna.aggarly.pricing.dto.CouponMetricsResponse response = couponService.getMetrics();
        return ApiResponse.ok(response, "Coupon metrics retrieved successfully").toResponseEntity();
    }

    @PostMapping("/{couponId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a coupon (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(@PathVariable UUID couponId) {
        couponService.deactivateCoupon(couponId);
        return ApiResponse.<Void>empty("Coupon deactivated successfully").toResponseEntity();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a coupon code and calculate discount amount", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ValidateCouponRequest request) {
        UUID userId = (principal != null) ? principal.getUserId() : null;
        CouponValidationResponse response = couponService.validateCoupon(request.code(), request.subtotal(), userId);
        return ApiResponse.ok(response, "Coupon validated successfully").toResponseEntity();
    }
}
