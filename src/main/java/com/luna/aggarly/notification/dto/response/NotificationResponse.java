package com.luna.aggarly.notification.dto.response;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.entity.enums.NotificationChannel;
import com.luna.aggarly.notification.entity.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationCategory category,
        NotificationChannel channel,
        String type,
        String title,
        String message,
        String dataJson,
        boolean read,
        Instant readAt,
        NotificationStatus status,
        Instant createdAt
) {}
