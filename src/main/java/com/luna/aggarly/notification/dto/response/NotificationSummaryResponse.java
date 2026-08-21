package com.luna.aggarly.notification.dto.response;

import java.util.List;

public record NotificationSummaryResponse(
        long unreadCount,
        List<NotificationResponse> recentNotifications
) {}
