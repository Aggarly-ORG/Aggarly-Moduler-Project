package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.UserSession;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface UserSessionService {

    List<UserSessionResponse> getActiveSessions(UUID userId, UUID currentSessionId);

    UserSession createSession(User user, UUID familyId, HttpServletRequest request);

    void updateActivity(UUID familyId);

    void revokeSession(UUID userId, UUID sessionId);

    void revokeAllOtherSessions(UUID userId, UUID currentSessionId);

    void revokeAllSessions(UUID userId);

    void revokeSessionByFamilyId(UUID familyId);

    UUID getSessionIdByFamilyId(UUID familyId);
}
