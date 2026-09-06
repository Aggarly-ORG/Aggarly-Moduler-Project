package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedFalseOrderByLastActiveAtDesc(UUID userId);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserSession> findByFamilyId(UUID familyId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true, s.revokedAt = :now WHERE s.id = :sessionId AND s.revoked = false")
    int revokeSession(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.id <> :currentSessionId AND s.revoked = false")
    int revokeAllOtherSessions(@Param("userId") UUID userId, @Param("currentSessionId") UUID currentSessionId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.revoked = false")
    int revokeAllUserSessions(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActiveAt = :now WHERE s.familyId = :familyId AND s.revoked = false")
    int updateActivity(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
