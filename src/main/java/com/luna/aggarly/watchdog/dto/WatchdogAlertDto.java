package com.luna.aggarly.watchdog.dto;

import java.math.BigDecimal;

public record WatchdogAlertDto(
        String id,
        String sanctuaryTitle,
        String sanctuaryLocation,
        String bortleRating,
        String targetDates,
        BigDecimal originalPrice,
        BigDecimal targetPrice,
        BigDecimal currentPrice,
        String status,
        String createdAt,
        String imageUrl,
        Boolean solsticeTrigger,
        String notificationsChannel
) {}