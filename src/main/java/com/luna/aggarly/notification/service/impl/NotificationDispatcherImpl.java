package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.entity.Notification;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.entity.enums.NotificationChannel;
import com.luna.aggarly.notification.entity.enums.NotificationStatus;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.NotificationRepository;
import com.luna.aggarly.notification.service.EmailSenderService;
import com.luna.aggarly.notification.service.InAppPushService;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.notification.service.UserNotificationPreferenceService;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferenceService preferenceService;
    private final InAppPushService inAppPushService;
    private final EmailSenderService emailSenderService;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void dispatch(UUID userId,
                         NotificationCategory category,
                         String type,
                         String title,
                         String message,
                         String dataJson,
                         String emailTemplateName,
                         Map<String, Object> templateModel) {
        log.info("Dispatching notification to user {}, category {}, type {}", userId, category, type);

        // 1. In-App Notification channel
        if (preferenceService.isInAppEnabled(userId, category)) {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .category(category)
                    .channel(NotificationChannel.IN_APP)
                    .type(type)
                    .title(title)
                    .message(message)
                    .dataJson(dataJson)
                    .read(false)
                    .status(NotificationStatus.SENT)
                    .build();

            Notification saved = notificationRepository.save(notification);
            NotificationResponse response = notificationMapper.toResponse(saved);

            // Push in real-time over WebSocket STOMP
            inAppPushService.pushNotification(userId, response);
        } else {
            log.debug("In-app notification suppressed by user preference for user {}, category {}", userId, category);
        }

        // 2. Email Notification channel
        if (emailTemplateName != null && preferenceService.isEmailEnabled(userId, category)) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                String email = userOpt.get().getEmail();
                emailSenderService.sendHtmlEmail(email, title, emailTemplateName, templateModel);
            } else {
                log.warn("Cannot send email: user {} not found", userId);
            }
        } else {
            log.debug("Email notification suppressed or no template for user {}, category {}", userId, category);
        }
    }
}
