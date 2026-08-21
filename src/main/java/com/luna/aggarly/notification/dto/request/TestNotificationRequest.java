package com.luna.aggarly.notification.dto.request;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;

public record TestNotificationRequest(
        String title,
        String message,
        NotificationCategory category,
        String type,
        String dataJson
) {}
