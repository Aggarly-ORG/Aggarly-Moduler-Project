package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.UserSession;
import com.luna.aggarly.user.exceptions.VerificationException;
import com.luna.aggarly.user.repository.RefreshTokenRepository;
import com.luna.aggarly.user.repository.UserSessionRepository;
import com.luna.aggarly.user.service.impl.UserSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserSessionServiceImpl userSessionService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .email("user@example.com")
                .username("testuser")
                .build();
        ReflectionTestUtils.setField(testUser, "id", userId);
    }

    @Test
    @DisplayName("getActiveSessions should map sessions and set isCurrent correctly")
    void getActiveSessions_ShouldMapSessionsAndSetIsCurrent() {
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();

        UserSession session1 = UserSession.builder()
                .user(testUser)
                .familyId(UUID.randomUUID())
                .deviceName("Chrome on Windows 10/11")
                .ipAddress("192.168.1.50")
                .location("Localhost / Dev")
                .lastActiveAt(Instant.now())
                .revoked(false)
                .build();
        ReflectionTestUtils.setField(session1, "id", currentSessionId);

        UserSession session2 = UserSession.builder()
                .user(testUser)
                .familyId(UUID.randomUUID())
                .deviceName("Safari on iPhone")
                .ipAddress("10.0.0.1")
                .location("Private Network")
                .lastActiveAt(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();
        ReflectionTestUtils.setField(session2, "id", otherSessionId);

        when(userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId))
                .thenReturn(List.of(session1, session2));

        List<UserSessionResponse> responses = userSessionService.getActiveSessions(userId, currentSessionId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(currentSessionId);
        assertThat(responses.get(0).isCurrent()).isTrue();
        assertThat(responses.get(1).id()).isEqualTo(otherSessionId);
        assertThat(responses.get(1).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("createSession should parse device, IP, location and save session")
    void createSession_ShouldParseDeviceAndSave() {
        UUID familyId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0");
        request.setRemoteAddr("127.0.0.1");

        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> {
            UserSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
            return s;
        });

        UserSession saved = userSessionService.createSession(testUser, familyId, request);

        assertThat(saved).isNotNull();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getFamilyId()).isEqualTo(familyId);
        assertThat(saved.getDeviceName()).contains("Chrome");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getLocation()).isEqualTo("Localhost / Dev");
        assertThat(saved.isRevoked()).isFalse();
        verify(userSessionRepository, times(1)).save(any(UserSession.class));
    }

    @Test
    @DisplayName("updateActivity should call repository updateActivity with familyId")
    void updateActivity_ShouldCallRepository() {
        UUID familyId = UUID.randomUUID();
        userSessionService.updateActivity(familyId);
        verify(userSessionRepository, times(1)).updateActivity(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("revokeSession should revoke session and corresponding token family")
    void revokeSession_ShouldRevokeSessionAndFamily() {
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        UserSession session = UserSession.builder()
                .user(testUser)
                .familyId(familyId)
                .revoked(false)
                .build();
        ReflectionTestUtils.setField(session, "id", sessionId);

        when(userSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        userSessionService.revokeSession(userId, sessionId);

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.getRevokedAt()).isNotNull();
        verify(userSessionRepository, times(1)).save(session);
        verify(refreshTokenRepository, times(1)).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("revokeSession should throw VerificationException if session not found")
    void revokeSession_ShouldThrowExceptionIfNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(userSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSessionService.revokeSession(userId, sessionId))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("Active session not found");

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("revokeAllOtherSessions should revoke all sessions except the current one")
    void revokeAllOtherSessions_ShouldRevokeOnlyOtherSessions() {
        UUID currentSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        UUID otherFamilyId = UUID.randomUUID();

        UserSession current = UserSession.builder()
                .user(testUser)
                .familyId(UUID.randomUUID())
                .revoked(false)
                .build();
        ReflectionTestUtils.setField(current, "id", currentSessionId);

        UserSession other = UserSession.builder()
                .user(testUser)
                .familyId(otherFamilyId)
                .revoked(false)
                .build();
        ReflectionTestUtils.setField(other, "id", otherSessionId);

        when(userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId))
                .thenReturn(List.of(current, other));

        userSessionService.revokeAllOtherSessions(userId, currentSessionId);

        assertThat(current.isRevoked()).isFalse();
        assertThat(other.isRevoked()).isTrue();
        assertThat(other.getRevokedAt()).isNotNull();

        verify(userSessionRepository, times(1)).save(other);
        verify(userSessionRepository, never()).save(current);
        verify(refreshTokenRepository, times(1)).revokeFamily(eq(otherFamilyId), any(Instant.class));
    }

    @Test
    @DisplayName("revokeAllSessions should revoke all active sessions for user")
    void revokeAllSessions_ShouldRevokeAllUserSessions() {
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();

        UserSession s1 = UserSession.builder().user(testUser).familyId(family1).revoked(false).build();
        UserSession s2 = UserSession.builder().user(testUser).familyId(family2).revoked(false).build();

        when(userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId))
                .thenReturn(List.of(s1, s2));

        userSessionService.revokeAllSessions(userId);

        assertThat(s1.isRevoked()).isTrue();
        assertThat(s2.isRevoked()).isTrue();
        verify(userSessionRepository, times(2)).save(any(UserSession.class));
        verify(refreshTokenRepository, times(1)).revokeFamily(eq(family1), any(Instant.class));
        verify(refreshTokenRepository, times(1)).revokeFamily(eq(family2), any(Instant.class));
    }

    @Test
    @DisplayName("getSessionIdByFamilyId should return session id when found")
    void getSessionIdByFamilyId_ShouldReturnSessionIdWhenFound() {
        UUID familyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UserSession session = UserSession.builder().build();
        ReflectionTestUtils.setField(session, "id", sessionId);

        when(userSessionRepository.findByFamilyId(familyId)).thenReturn(Optional.of(session));

        UUID result = userSessionService.getSessionIdByFamilyId(familyId);
        assertThat(result).isEqualTo(sessionId);
    }
}
