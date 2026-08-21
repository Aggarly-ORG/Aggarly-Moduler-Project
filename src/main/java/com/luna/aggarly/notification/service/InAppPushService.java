package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.response.NotificationResponse;

import java.util.UUID;

public interface InAppPushService {

    void pushNotification(UUID userId, NotificationResponse notification);
}
