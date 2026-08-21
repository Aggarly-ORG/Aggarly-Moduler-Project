package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.entity.enums.NotificationCategory;

import java.util.Map;
import java.util.UUID;

public interface NotificationDispatcher {

    void dispatch(UUID userId,
                  NotificationCategory category,
                  String type,
                  String title,
                  String message,
                  String dataJson,
                  String emailTemplateName,
                  Map<String, Object> templateModel);
}
