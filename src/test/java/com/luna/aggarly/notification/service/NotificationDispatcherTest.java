package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.response.NotificationResponse;
import com.luna.aggarly.notification.entity.Notification;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.entity.enums.NotificationChannel;
import com.luna.aggarly.notification.entity.enums.NotificationStatus;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.NotificationRepository;
import com.luna.aggarly.notification.service.impl.NotificationDispatcherImpl;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationPreferenceService preferenceService;

    @Mock
    private InAppPushService inAppPushService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationDispatcherImpl notificationDispatcher;

    private UUID userId;
    private User sampleUser;
    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .email("traveler@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        sampleUser.setId(userId);

        sampleNotification = Notification.builder()
                .userId(userId)
                .category(NotificationCategory.BOOKING)
                .channel(NotificationChannel.IN_APP)
                .type("BOOKING_CONFIRMED")
                .title("Booking Confirmed")
                .message("Your stay is confirmed.")
                .status(NotificationStatus.SENT)
                .build();
        sampleNotification.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("dispatch should save in-app notification and trigger WebSocket push when enabled")
    void testDispatchInAppEnabled() {
        when(preferenceService.isInAppEnabled(userId, NotificationCategory.BOOKING)).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenReturn(sampleNotification);
        when(notificationMapper.toResponse(sampleNotification)).thenReturn(new NotificationResponse(
                sampleNotification.getId(), userId, NotificationCategory.BOOKING, NotificationChannel.IN_APP,
                "BOOKING_CONFIRMED", "Booking Confirmed", "Your stay is confirmed.", null, false, null, NotificationStatus.SENT, Instant.now()
        ));

        notificationDispatcher.dispatch(userId, NotificationCategory.BOOKING, "BOOKING_CONFIRMED", "Booking Confirmed", "Your stay is confirmed.", null, null, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(inAppPushService).pushNotification(eq(userId), any());
    }

    @Test
    @DisplayName("dispatch should send async email when email channel enabled and template provided")
    void testDispatchEmailEnabled() {
        when(preferenceService.isInAppEnabled(userId, NotificationCategory.BOOKING)).thenReturn(false);
        when(preferenceService.isEmailEnabled(userId, NotificationCategory.BOOKING)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        Map<String, Object> model = new HashMap<>();
        model.put("name", "John");

        notificationDispatcher.dispatch(userId, NotificationCategory.BOOKING, "BOOKING_CONFIRMED", "Booking Confirmed", "Your stay is confirmed.", null, "booking-confirmed", model);

        verify(emailSenderService).sendHtmlEmail("traveler@example.com", "Booking Confirmed", "booking-confirmed", model);
    }
}
