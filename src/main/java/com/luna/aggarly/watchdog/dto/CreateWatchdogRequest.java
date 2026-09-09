package com.luna.aggarly.watchdog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWatchdogRequest(
        UUID propertyId,
        String sanctuaryTitle,
        String sanctuaryLocation,
        String bortleRating,
        String targetDates,
        BigDecimal originalPrice,
        BigDecimal targetPrice,
        BigDecimal targetNightlyCeiling,
        String notificationsChannel,
        Boolean solsticeTrigger
) {}