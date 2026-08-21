package com.luna.aggarly.notification.repository;

import com.luna.aggarly.notification.entity.UserAlert;
import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, UUID> {

    List<UserAlert> findByUserIdAndActiveTrue(UUID userId);

    List<UserAlert> findByActiveTrueAndAlertType(AlertWatchType alertType);

    @Query("SELECT ua FROM UserAlert ua WHERE ua.active = true AND ua.alertType = 'PRICE_DROP' AND (ua.propertyId = :propertyId OR ua.propertyId IS NULL) AND (:price <= ua.targetPrice)")
    List<UserAlert> findMatchingPriceAlerts(@Param("propertyId") UUID propertyId, @Param("price") BigDecimal price);
}
