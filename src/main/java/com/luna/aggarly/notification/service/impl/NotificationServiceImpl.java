package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.dto.response.NotificationSummaryResponse;
import com.luna.aggarly.notification.entity.Notification;
import com.luna.aggarly.notification.exceptions.NotificationNotFoundException;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.NotificationRepository;
import com.luna.aggarly.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUnreadNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSummaryResponse getUnreadSummary(UUID userId) {
        long count = notificationRepository.countByUserIdAndReadFalse(userId);
        List<NotificationResponse> recent = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                .map(notificationMapper::toResponse)
                .getContent();
        return new NotificationSummaryResponse(count, recent);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationNotFoundException(notificationId);
        }

        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
        log.debug("Marked notification {} as read for user {}", notificationId, userId);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        int updated = notificationRepository.markAllAsRead(userId, Instant.now());
        log.info("Marked all (total: {}) notifications as read for user {}", updated, userId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new NotificationNotFoundException(notificationId);
        }

        notificationRepository.delete(notification);
        log.info("Deleted notification {} for user {}", notificationId, userId);
    }
}
