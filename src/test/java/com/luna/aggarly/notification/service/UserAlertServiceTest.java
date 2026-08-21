package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.request.CreateUserAlertRequest;
import com.luna.aggarly.notification.dto.response.UserAlertResponse;
import com.luna.aggarly.notification.entity.UserAlert;
import com.luna.aggarly.notification.entity.enums.AlertWatchType;
import com.luna.aggarly.notification.event.AiAlertTriggeredEvent;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.UserAlertRepository;
import com.luna.aggarly.notification.service.impl.UserAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAlertServiceTest {

    @Mock
    private UserAlertRepository alertRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserAlertServiceImpl userAlertService;

    private UUID userId;
    private UUID propertyId;
    private UUID alertId;
    private UserAlert sampleAlert;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        alertId = UUID.randomUUID();

        sampleAlert = UserAlert.builder()
                .userId(userId)
                .alertType(AlertWatchType.PRICE_DROP)
                .propertyId(propertyId)
                .targetPrice(BigDecimal.valueOf(150.00))
                .active(true)
                .build();
        sampleAlert.setId(alertId);
    }

    @Test
    @DisplayName("createAlert should persist alert and return response")
    void testCreateAlert() {
        CreateUserAlertRequest request = new CreateUserAlertRequest(
                AlertWatchType.PRICE_DROP, propertyId, null, BigDecimal.valueOf(150.00)
        );

        when(alertRepository.save(any(UserAlert.class))).thenReturn(sampleAlert);
        when(notificationMapper.toResponse(sampleAlert)).thenReturn(new UserAlertResponse(
                alertId, userId, AlertWatchType.PRICE_DROP, propertyId, null, BigDecimal.valueOf(150.00), true, null, Instant.now()
        ));

        UserAlertResponse response = userAlertService.createAlert(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(alertId);
        verify(alertRepository).save(any(UserAlert.class));
    }

    @Test
    @DisplayName("evaluatePriceChange should find matching alerts and publish AiAlertTriggeredEvent")
    void testEvaluatePriceChange() {
        BigDecimal newPrice = BigDecimal.valueOf(120.00);
        when(alertRepository.findMatchingPriceAlerts(propertyId, newPrice)).thenReturn(List.of(sampleAlert));
        when(alertRepository.save(any(UserAlert.class))).thenReturn(sampleAlert);

        userAlertService.evaluatePriceChange(propertyId, newPrice, "Cozy Villa");

        verify(alertRepository).save(sampleAlert);
        verify(eventPublisher).publishEvent(any(AiAlertTriggeredEvent.class));
    }
}
