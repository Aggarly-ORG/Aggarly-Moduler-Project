package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.UserSession;
import com.luna.aggarly.user.exceptions.VerificationException;
import com.luna.aggarly.user.repository.RefreshTokenRepository;
import com.luna.aggarly.user.repository.UserSessionRepository;
import com.luna.aggarly.user.service.UserSessionService;
import com.luna.aggarly.user.utils.DeviceUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionResponse> getActiveSessions(UUID userId, UUID currentSessionId) {
        return userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId)
                .stream()
                .map(session -> UserSessionResponse.fromEntity(
                        session,
                        currentSessionId != null && currentSessionId.equals(session.getId())
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserSession createSession(User user, UUID familyId, HttpServletRequest request) {
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String deviceName = DeviceUtils.parseDeviceName(userAgent);
        String ipAddress = DeviceUtils.extractClientIp(request);
        String location = DeviceUtils.resolveLocation(ipAddress);

        // Proactively clean up superseded duplicate sessions from the same device & IP for this user
        if (deviceName != null && ipAddress != null && user.getId() != null) {
            List<UserSession> duplicateDeviceSessions = userSessionRepository
                    .findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(user.getId())
                    .stream()
                    .filter(s -> deviceName.equalsIgnoreCase(s.getDeviceName()) && ipAddress.equals(s.getIpAddress()))
                    .toList();

            Instant now = Instant.now();
            for (UserSession oldSession : duplicateDeviceSessions) {
                oldSession.setRevoked(true);
                oldSession.setRevokedAt(now);
                userSessionRepository.save(oldSession);
                if (oldSession.getFamilyId() != null) {
                    refreshTokenRepository.revokeFamily(oldSession.getFamilyId(), now);
                }
                log.info("🧹 Superseded prior session {} for device '{}' / IP '{}'",
                        oldSession.getId(), deviceName, ipAddress);
            }
        }

        UserSession session = UserSession.builder()
                .user(user)
                .familyId(familyId)
                .deviceName(deviceName)
                .ipAddress(ipAddress)
                .location(location)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .lastActiveAt(Instant.now())
                .revoked(false)
                .build();

        UserSession saved = userSessionRepository.save(session);
        log.info("🖥️ Active session created: user={}, device='{}', ip='{}', sessionId={}",
                user.getEmail(), deviceName, ipAddress, saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void updateActivity(UUID familyId) {
        if (familyId == null) return;
        userSessionRepository.updateActivity(familyId, Instant.now());
    }

    @Override
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        UserSession session = userSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new VerificationException("Active session not found: " + sessionId));

        Instant now = Instant.now();
        session.setRevoked(true);
        session.setRevokedAt(now);
        userSessionRepository.save(session);

        // Revoke underlying refresh token family
        refreshTokenRepository.revokeFamily(session.getFamilyId(), now);
        log.info("🔒 Device session revoked: user={}, sessionId={}, familyId={}",
                userId, sessionId, session.getFamilyId());
    }

    @Override
    @Transactional
    public void revokeAllOtherSessions(UUID userId, UUID currentSessionId) {
        Instant now = Instant.now();

        // Find other active sessions to revoke their token families
        List<UserSession> otherSessions = userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId)
                .stream()
                .filter(s -> currentSessionId == null || !s.getId().equals(currentSessionId))
                .toList();

        for (UserSession s : otherSessions) {
            s.setRevoked(true);
            s.setRevokedAt(now);
            userSessionRepository.save(s);
            refreshTokenRepository.revokeFamily(s.getFamilyId(), now);
        }

        log.info("🚪 Signed out {} other session(s) for user={}", otherSessions.size(), userId);
    }

    @Override
    @Transactional
    public void revokeAllSessions(UUID userId) {
        Instant now = Instant.now();
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(userId);
        for (UserSession s : activeSessions) {
            s.setRevoked(true);
            s.setRevokedAt(now);
            userSessionRepository.save(s);
            refreshTokenRepository.revokeFamily(s.getFamilyId(), now);
        }
        log.info("🚪 Revoked all {} active session(s) for user={}", activeSessions.size(), userId);
    }

    @Override
    @Transactional
    public void revokeSessionByFamilyId(UUID familyId) {
        if (familyId == null) return;
        userSessionRepository.findByFamilyId(familyId).ifPresent(s -> {
            s.setRevoked(true);
            s.setRevokedAt(Instant.now());
            userSessionRepository.save(s);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getSessionIdByFamilyId(UUID familyId) {
        if (familyId == null) return null;
        return userSessionRepository.findByFamilyId(familyId)
                .map(UserSession::getId)
                .orElse(null);
    }
}
