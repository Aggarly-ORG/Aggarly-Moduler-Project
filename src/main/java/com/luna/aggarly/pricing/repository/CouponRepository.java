package com.luna.aggarly.pricing.repository;

import com.luna.aggarly.pricing.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCodeAndActiveTrue(String code);
}
