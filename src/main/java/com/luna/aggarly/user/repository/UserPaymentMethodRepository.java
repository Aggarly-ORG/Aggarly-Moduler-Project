package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.UserPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPaymentMethodRepository extends JpaRepository<UserPaymentMethod, UUID> {
    List<UserPaymentMethod> findByUserIdOrderByIsDefaultDescCreatedAtDesc(UUID userId);
    Optional<UserPaymentMethod> findByIdAndUserId(UUID id, UUID userId);
    Optional<UserPaymentMethod> findByUserIdAndStripePaymentMethodId(UUID userId, String stripePaymentMethodId);
}
