package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.dto.response.NotificationSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    Page<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable);

    Page<NotificationResponse> getUnreadNotifications(UUID userId, Pageable pageable);

    NotificationSummaryResponse getUnreadSummary(UUID userId);

    void markAsRead(UUID notificationId, UUID userId);

    int markAllAsRead(UUID userId);

    void deleteNotification(UUID notificationId, UUID userId);
}
