package com.luna.aggarly.notification.service;

import com.luna.aggarly.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.luna.aggarly.notification.dto.response.NotificationPreferenceResponse;
import com.luna.aggarly.notification.entity.UserNotificationPreference;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.mapper.NotificationMapper;
import com.luna.aggarly.notification.repository.UserNotificationPreferenceRepository;
import com.luna.aggarly.notification.service.impl.UserNotificationPreferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationPreferenceServiceTest {

    @Mock
    private UserNotificationPreferenceRepository preferenceRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private UserNotificationPreferenceServiceImpl preferenceService;

    private UUID userId;
    private UserNotificationPreference samplePref;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        samplePref = UserNotificationPreference.builder()
                .userId(userId)
                .category(NotificationCategory.BOOKING)
                .inAppEnabled(true)
                .emailEnabled(true)
                .pushEnabled(true)
                .smsEnabled(false)
                .build();
    }

    @Test
    @DisplayName("getUserPreferences should initialize defaults when none exist")
    void testGetUserPreferencesInitializesDefaults() {
        when(preferenceRepository.findByUserId(userId)).thenReturn(List.of());
        when(preferenceRepository.saveAll(any())).thenReturn(List.of(samplePref));
        when(notificationMapper.toPreferenceResponseList(any())).thenReturn(List.of(
                new NotificationPreferenceResponse(UUID.randomUUID(), userId, NotificationCategory.BOOKING, true, true, true, false)
        ));

        List<NotificationPreferenceResponse> responses = preferenceService.getUserPreferences(userId);

        assertThat(responses).isNotEmpty();
        verify(preferenceRepository).saveAll(any());
    }

    @Test
    @DisplayName("updatePreference should modify specific channel flags")
    void testUpdatePreference() {
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest(
                NotificationCategory.BOOKING, true, false, false, false
        );

        when(preferenceRepository.findByUserIdAndCategory(userId, NotificationCategory.BOOKING))
                .thenReturn(Optional.of(samplePref));
        when(preferenceRepository.save(any(UserNotificationPreference.class))).thenReturn(samplePref);
        when(notificationMapper.toResponse(samplePref)).thenReturn(
                new NotificationPreferenceResponse(UUID.randomUUID(), userId, NotificationCategory.BOOKING, true, false, false, false)
        );

        NotificationPreferenceResponse response = preferenceService.updatePreference(userId, request);

        assertThat(response).isNotNull();
        assertThat(samplePref.isEmailEnabled()).isFalse();
        assertThat(samplePref.isPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEmailEnabled should always return true for SECURITY category")
    void testSecurityCategoryAlwaysTrue() {
        boolean enabled = preferenceService.isEmailEnabled(userId, NotificationCategory.SECURITY);
        assertThat(enabled).isTrue();
    }
}
