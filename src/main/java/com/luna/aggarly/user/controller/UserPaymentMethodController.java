package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.SavePaymentMethodRequest;
import com.luna.aggarly.user.dto.UserPaymentMethodResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserPaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/payment-methods")
@RequiredArgsConstructor
@Tag(name = "User Payment Methods", description = "Saved Cards & Payment Methods Management")
public class UserPaymentMethodController {

    private final UserPaymentMethodService paymentMethodService;

    @GetMapping
    @Operation(summary = "Get all saved payment methods for current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserPaymentMethodResponse>>> getUserPaymentMethods(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        List<UserPaymentMethodResponse> methods = paymentMethodService.getUserPaymentMethods(userId);
        return ApiResponse.ok(methods, "Saved payment methods retrieved successfully").toResponseEntity();
    }

    @PostMapping
    @Operation(summary = "Save a new payment method", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserPaymentMethodResponse>> savePaymentMethod(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SavePaymentMethodRequest request) {
        UUID userId = principal.getUserId();
        UserPaymentMethodResponse saved = paymentMethodService.savePaymentMethod(userId, request);
        return ApiResponse.created(saved, "Payment method saved successfully").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved payment method", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deletePaymentMethod(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID userId = principal.getUserId();
        paymentMethodService.deletePaymentMethod(userId, id);
        return ApiResponse.<Void>empty("Payment method deleted successfully").toResponseEntity();
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Set default payment method", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> setDefaultPaymentMethod(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UUID userId = principal.getUserId();
        paymentMethodService.setDefaultPaymentMethod(userId, id);
        return ApiResponse.<Void>empty("Default payment method updated successfully").toResponseEntity();
    }
}
