package com.luna.aggarly.pricing.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE coupons SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Coupon extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code; // e.g. "SUMMER10", stored uppercase

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentType adjustmentType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal adjustmentValue;

    private Instant expiresAt; // null = never expires

    private Integer maxRedemptions; // null = unlimited

    @Column(nullable = false)
    private int currentRedemptions;

    @Column(nullable = false)
    private boolean active;

    private BigDecimal minSubtotal; // coupon only valid above this subtotal, nullable
}
