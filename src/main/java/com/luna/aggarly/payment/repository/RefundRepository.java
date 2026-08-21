package com.luna.aggarly.payment.repository;

import com.luna.aggarly.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    List<Refund> findByPaymentId(UUID paymentId);
}