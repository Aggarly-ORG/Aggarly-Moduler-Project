package com.luna.aggarly.payment.service;

import com.luna.aggarly.payment.dto.PaymentIntentResponse;
import com.luna.aggarly.payment.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

import com.luna.aggarly.payment.dto.RefundResponse;
import com.luna.aggarly.payment.gateway.GatewayEvent;

import com.luna.aggarly.payment.dto.PaymentDetailResponse;
import com.luna.aggarly.payment.dto.PaymentResponse;
import com.luna.aggarly.payment.dto.EarningsSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(UUID bookingId, UUID userId, BigDecimal amount, String currency, String idempotencyKey);
    void cancelPayment(UUID bookingId, String idempotencyKey);
    PaymentStatus getPaymentStatus(UUID bookingId);
    void handleWebhookEvent(GatewayEvent event);
    RefundResponse refund(UUID bookingId, BigDecimal amount, String reason, String idempotencyKey);
    PaymentDetailResponse getPaymentDetails(UUID bookingId);
    PaymentStatus confirmPaymentForBooking(UUID bookingId, UUID userId);
    PaymentStatus confirmPaymentForBooking(UUID bookingId, UUID userId, String paymentMethodId);
    Page<PaymentResponse> getUserPayments(UUID userId, Pageable pageable);
    EarningsSummaryResponse getEarningsSummary(String currency);
    Page<PaymentResponse> getAllPayments(PaymentStatus status, String currency, java.time.Instant startDate, java.time.Instant endDate, Pageable pageable);
    com.luna.aggarly.payment.dto.AdminFinancialMetricsResponse getFinancialMetrics(String currency, String period);
    byte[] exportTaxLedgerCsv(Integer year, Integer quarter);
    PaymentDetailResponse overrideHold(UUID bookingId);
}
