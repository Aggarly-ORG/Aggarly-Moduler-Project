package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.luna.aggarly.notification.dto.response.NotificationPreferenceResponse;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;

import java.util.List;
import java.util.UUID;

public interface UserNotificationPreferenceService {

    List<NotificationPreferenceResponse> getUserPreferences(UUID userId);

    NotificationPreferenceResponse updatePreference(UUID userId, UpdateNotificationPreferenceRequest request);

    boolean isEmailEnabled(UUID userId, NotificationCategory category);

    boolean isInAppEnabled(UUID userId, NotificationCategory category);
}
