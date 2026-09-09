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
        Instant createdAt,
        boolean unread,
        String body,
        String badgeText,
        String badgeIcon,
        String timeAgo,
        String actionUrl,
        String actionLabel,
        String metaInfo
) {
    public NotificationResponse(
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
    ) {
        this(
                id,
                userId,
                category,
                channel,
                type,
                title,
                message,
                dataJson,
                read,
                readAt,
                status,
                createdAt,
                !read,
                message,
                category != null ? category.name() + " DISPATCH" : "SYSTEM DISPATCH",
                "info",
                "Recently",
                null,
                null,
                null
        );
    }
}
