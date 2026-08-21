package com.luna.aggarly.notification.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted=false")
@SQLDelete(sql = "UPDATE user_alerts SET is_deleted = true WHERE id = ?")
public class UserAlert extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertWatchType alertType;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "target_price", precision = 10, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "triggered_at")
    private Instant triggeredAt;
}
