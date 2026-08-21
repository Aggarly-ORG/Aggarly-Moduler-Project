package com.luna.aggarly.notification.dto.request;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull(message = "Category is required")
        NotificationCategory category,

        Boolean inAppEnabled,

        Boolean emailEnabled,

        Boolean pushEnabled,

        Boolean smsEnabled
) {}
