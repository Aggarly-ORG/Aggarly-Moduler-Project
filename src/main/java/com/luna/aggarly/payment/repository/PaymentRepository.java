package com.luna.aggarly.payment.repository;

import com.luna.aggarly.payment.entity.Payment;
import com.luna.aggarly.payment.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findByGatewayPaymentIntentId(String gatewayPaymentIntentId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Page<Payment> findByUserId(UUID userId, Pageable pageable);
    List<Payment> findByCurrencyAndStatus(String currency, PaymentStatus status);
}