package com.luna.aggarly.payment.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.payment.dto.EarningsSummaryResponse;
import com.luna.aggarly.payment.dto.PaymentDetailResponse;
import com.luna.aggarly.payment.dto.PaymentResponse;
import com.luna.aggarly.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for administrative payment analytics, summaries, and audit logs.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Payments", description = "Administrative Payment Audit & Earnings APIs")
public class PaymentAdminController {

    private final PaymentService paymentService;

    @GetMapping("/{bookingId}/details")
    @Operation(summary = "Get detailed payment audit log for a booking (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> getPaymentDetails(@PathVariable UUID bookingId) {
        PaymentDetailResponse response = paymentService.getPaymentDetails(bookingId);
        return ApiResponse.ok(response, "Payment details retrieved successfully").toResponseEntity();
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get all payments associated with a specific user (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getUserPayments(
            @PathVariable UUID userId,
            Pageable pageable) {
        Page<PaymentResponse> page = paymentService.getUserPayments(userId, pageable);
        return ApiResponse.paged(page, "User payments retrieved successfully").toResponseEntity();
    }

    @GetMapping("/earnings/summary")
    @Operation(summary = "Get platform earnings summary (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<EarningsSummaryResponse>> getEarningsSummary(
            @RequestParam(defaultValue = "USD") String currency) {
        EarningsSummaryResponse response = paymentService.getEarningsSummary(currency);
        return ApiResponse.ok(response, "Earnings summary calculated successfully").toResponseEntity();
    }
}
