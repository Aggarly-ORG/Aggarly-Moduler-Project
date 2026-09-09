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

    @GetMapping
    @Operation(summary = "Query global payments ledger (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAllPayments(
            @RequestParam(required = false) com.luna.aggarly.payment.entity.enums.PaymentStatus status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) java.time.Instant startDate,
            @RequestParam(required = false) java.time.Instant endDate,
            Pageable pageable) {
        Page<PaymentResponse> page = paymentService.getAllPayments(status, currency, startDate, endDate, pageable);
        return ApiResponse.paged(page, "Global ledger retrieved successfully").toResponseEntity();
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get executive financial and commercial KPIs (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<com.luna.aggarly.payment.dto.AdminFinancialMetricsResponse>> getFinancialMetrics(
            @RequestParam(defaultValue = "EUR") String currency,
            @RequestParam(defaultValue = "30d") String period) {
        com.luna.aggarly.payment.dto.AdminFinancialMetricsResponse metrics = paymentService.getFinancialMetrics(currency, period);
        return ApiResponse.ok(metrics, "Financial metrics retrieved successfully").toResponseEntity();
    }

    @GetMapping("/export")
    @Operation(summary = "Export tax ledger and transaction report as CSV (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public org.springframework.http.ResponseEntity<byte[]> exportTaxLedger(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        byte[] csv = paymentService.exportTaxLedgerCsv(year, quarter);
        String filename = String.format("aggarly_tax_ledger_%s_Q%s.csv",
                year != null ? year : 2026,
                quarter != null ? quarter : 1);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{bookingId}/override-hold")
    @Operation(summary = "Administrative override hold & force release escrow (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PaymentDetailResponse>> overrideHold(@PathVariable UUID bookingId) {
        PaymentDetailResponse detail = paymentService.overrideHold(bookingId);
        return ApiResponse.ok(detail, "Hold overridden and escrow released successfully").toResponseEntity();
    }
}
