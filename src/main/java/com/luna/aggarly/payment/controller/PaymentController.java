package com.luna.aggarly.payment.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.payment.dto.PaymentIntentResponse;
import com.luna.aggarly.payment.dto.PaymentRequest;
import com.luna.aggarly.payment.dto.PaymentStatusResponse;
import com.luna.aggarly.payment.dto.RefundRequest;
import com.luna.aggarly.payment.dto.RefundResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller managing payment intents, confirmations, cancellations, and refund operations.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Checkout, Intent Creation, Confirmation & Refund APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intent")
    @Operation(summary = "Create payment intent for a booking", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentRequest request) {
        PaymentIntentResponse response = paymentService.createPaymentIntent(
                request.getBookingId(),
                principal.getUserId(),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey()
        );
        return ApiResponse.ok(response, "Payment intent created successfully").toResponseEntity();
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a pending payment intent", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> cancelPayment(
            @PathVariable UUID bookingId,
            @RequestParam String idempotencyKey) {
        paymentService.cancelPayment(bookingId, idempotencyKey);
        return ApiResponse.<Void>empty("Payment intent cancelled successfully").toResponseEntity();
    }

    @PostMapping("/{bookingId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process refund for a booking payment (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @PathVariable UUID bookingId,
            @Valid @RequestBody RefundRequest request,
            @RequestParam String idempotencyKey) {
        RefundResponse response = paymentService.refund(
                bookingId,
                request.getAmount(),
                request.getReason(),
                idempotencyKey
        );
        return ApiResponse.ok(response, "Refund processed successfully").toResponseEntity();
    }

    @PostMapping("/{bookingId}/confirm")
    @Operation(summary = "Confirm payment with a payment method ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> confirmPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) PaymentRequest request) {
        String pmId = request != null ? request.getPaymentMethodId() : null;
        PaymentStatusResponse response = new PaymentStatusResponse(
                paymentService.confirmPaymentForBooking(bookingId, principal.getUserId(), pmId));
        return ApiResponse.ok(response, "Payment confirmation processed").toResponseEntity();
    }

    @GetMapping("/{bookingId}/status")
    @Operation(summary = "Get current payment status for a booking")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(@PathVariable UUID bookingId) {
        PaymentStatusResponse response = new PaymentStatusResponse(paymentService.getPaymentStatus(bookingId));
        return ApiResponse.ok(response, "Payment status retrieved successfully").toResponseEntity();
    }
}
