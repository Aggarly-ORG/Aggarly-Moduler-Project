package com.luna.aggarly.payment.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.payment.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @Builder @RequiredArgsConstructor @AllArgsConstructor
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE payments SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
public class Payment extends BaseEntity {
    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "gateway_payment_intent_id")
    private String gatewayPaymentIntentId;

    @Column(name = "gateway_customer_id")
    private String gatewayCustomerId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "total_refunded_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalRefundedAmount = BigDecimal.ZERO;
}
