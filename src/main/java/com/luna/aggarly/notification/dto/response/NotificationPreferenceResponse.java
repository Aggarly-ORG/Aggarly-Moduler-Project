package com.luna.aggarly.notification.dto.response;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        UUID userId,
        NotificationCategory category,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean pushEnabled,
        boolean smsEnabled
) {}
