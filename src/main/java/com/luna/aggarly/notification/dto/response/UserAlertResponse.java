package com.luna.aggarly.notification.dto.response;

import com.luna.aggarly.notification.entity.enums.AlertWatchType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserAlertResponse(
        UUID id,
        UUID userId,
        AlertWatchType alertType,
        UUID propertyId,
        String city,
        BigDecimal targetPrice,
        boolean active,
        Instant triggeredAt,
        Instant createdAt
) {}
