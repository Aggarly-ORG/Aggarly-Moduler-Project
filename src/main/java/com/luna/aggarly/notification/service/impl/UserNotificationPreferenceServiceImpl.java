package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.luna.aggarly.notification.dto.response.NotificationPreferenceResponse;
import com.luna.aggarly.notification.entity.UserNotificationPreference;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.UserNotificationPreferenceRepository;
import com.luna.aggarly.notification.service.UserNotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotificationPreferenceServiceImpl implements UserNotificationPreferenceService {

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public List<NotificationPreferenceResponse> getUserPreferences(UUID userId) {
        List<UserNotificationPreference> prefs = preferenceRepository.findByUserId(userId);
        if (prefs.isEmpty()) {
            prefs = initializeDefaultPreferences(userId);
        }
        return notificationMapper.toPreferenceResponseList(prefs);
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId, UpdateNotificationPreferenceRequest request) {
        UserNotificationPreference pref = preferenceRepository.findByUserIdAndCategory(userId, request.category())
                .orElseGet(() -> UserNotificationPreference.builder()
                        .userId(userId)
                        .category(request.category())
                        .build());

        if (request.inAppEnabled() != null) pref.setInAppEnabled(request.inAppEnabled());
        if (request.emailEnabled() != null) pref.setEmailEnabled(request.emailEnabled());
        if (request.pushEnabled() != null) pref.setPushEnabled(request.pushEnabled());
        if (request.smsEnabled() != null) pref.setSmsEnabled(request.smsEnabled());

        UserNotificationPreference saved = preferenceRepository.save(pref);
        log.info("Updated notification preferences for user {}, category {}", userId, request.category());
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailEnabled(UUID userId, NotificationCategory category) {
        // Security category emails cannot be disabled
        if (category == NotificationCategory.SECURITY) {
            return true;
        }
        return preferenceRepository.findByUserIdAndCategory(userId, category)
                .map(UserNotificationPreference::isEmailEnabled)
                .orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInAppEnabled(UUID userId, NotificationCategory category) {
        return preferenceRepository.findByUserIdAndCategory(userId, category)
                .map(UserNotificationPreference::isInAppEnabled)
                .orElse(true);
    }

    private List<UserNotificationPreference> initializeDefaultPreferences(UUID userId) {
        List<UserNotificationPreference> defaults = new ArrayList<>();
        for (NotificationCategory cat : NotificationCategory.values()) {
            defaults.add(UserNotificationPreference.builder()
                    .userId(userId)
                    .category(cat)
                    .inAppEnabled(true)
                    .emailEnabled(true)
                    .pushEnabled(true)
                    .smsEnabled(false)
                    .build());
        }
        return preferenceRepository.saveAll(defaults);
    }
}
