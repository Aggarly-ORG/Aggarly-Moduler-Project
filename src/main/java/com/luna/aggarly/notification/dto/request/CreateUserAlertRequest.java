package com.luna.aggarly.notification.dto.request;

import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateUserAlertRequest(
        @NotNull(message = "Alert type is required")
        AlertWatchType alertType,

        UUID propertyId,

        String city,

        BigDecimal targetPrice
) {}
