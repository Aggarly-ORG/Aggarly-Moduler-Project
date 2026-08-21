package com.luna.aggarly.payment.dto;

import com.luna.aggarly.payment.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID bookingId;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private BigDecimal totalRefundedAmount;
    private Instant capturedAt;
    private Instant createdAt;
}
