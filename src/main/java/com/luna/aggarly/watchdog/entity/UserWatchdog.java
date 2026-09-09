package com.luna.aggarly.watchdog.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "user_watchdogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWatchdog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "sanctuary_title")
    private String sanctuaryTitle;

    @Column(name = "sanctuary_location")
    private String sanctuaryLocation;

    @Column(name = "bortle_rating", length = 50)
    private String bortleRating;

    @Column(name = "target_dates", length = 100)
    private String targetDates;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "target_price", precision = 12, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "notifications_channel", length = 100)
    @Builder.Default
    private String notificationsChannel = "SMS & Push";

    @Column(name = "solstice_trigger")
    @Builder.Default
    private Boolean solsticeTrigger = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}