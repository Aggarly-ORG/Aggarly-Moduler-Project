package com.luna.aggarly.pricing.repository;

import com.luna.aggarly.pricing.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {
    Optional<CouponRedemption> findByCouponIdAndUserId(UUID couponId, UUID userId);
    boolean existsByCouponIdAndUserId(UUID couponId, UUID userId);
}
