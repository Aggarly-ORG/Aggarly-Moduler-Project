package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.dto.request.CreateUserAlertRequest;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;
import com.luna.aggarly.notification.entity.UserAlert;
import com.luna.aggarly.notification.event.AiAlertTriggeredEvent;
import com.luna.aggarly.notification.exceptions.NotificationNotFoundException;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.UserAlertRepository;
import com.luna.aggarly.notification.service.UserAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAlertServiceImpl implements UserAlertService {

    private final UserAlertRepository alertRepository;
    private final NotificationMapper notificationMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UserAlertResponse createAlert(UUID userId, CreateUserAlertRequest request) {
        log.info("Creating watchdog alert for user {}, type {}", userId, request.alertType());

        UserAlert alert = UserAlert.builder()
                .userId(userId)
                .alertType(request.alertType())
                .propertyId(request.propertyId())
                .city(request.city())
                .targetPrice(request.targetPrice())
                .active(true)
                .build();

        UserAlert saved = alertRepository.save(alert);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAlertResponse> getUserAlerts(UUID userId) {
        List<UserAlert> alerts = alertRepository.findByUserIdAndActiveTrue(userId);
        return notificationMapper.toAlertResponseList(alerts);
    }

    @Override
    @Transactional
    public void deleteAlert(UUID alertId, UUID userId) {
        UserAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new NotificationNotFoundException(alertId));

        if (!alert.getUserId().equals(userId)) {
            throw new NotificationNotFoundException(alertId);
        }

        alert.setActive(false);
        alertRepository.save(alert);
        log.info("Deactivated watchdog alert {} for user {}", alertId, userId);
    }

    @Override
    @Transactional
    public void evaluatePriceChange(UUID propertyId, BigDecimal newPrice, String propertyTitle) {
        List<UserAlert> matching = alertRepository.findMatchingPriceAlerts(propertyId, newPrice);
        log.info("Evaluating price change for property {}: {} matches found", propertyId, matching.size());

        for (UserAlert alert : matching) {
            alert.setTriggeredAt(Instant.now());
            alertRepository.save(alert);

            eventPublisher.publishEvent(new AiAlertTriggeredEvent(
                    this,
                    alert.getId(),
                    alert.getUserId(),
                    propertyId,
                    alert.getAlertType(),
                    "Price Drop Alert: " + propertyTitle,
                    String.format("Price dropped to $%s (target: $%s)", newPrice, alert.getTargetPrice()),
                    newPrice
            ));
        }
    }
}
