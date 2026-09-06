package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.RefreshToken;
import com.luna.aggarly.user.entity.User;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByFamilyId(UUID familyId);

    Optional<RefreshToken> findFirstByFamilyIdAndRevokedFalse(UUID familyId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.familyId = :familyId AND r.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.user = :user AND r.revoked = false")
    int revokeAllUserTokens(@Param("user") User user, @Param("now") Instant now);

    default int revokeAllUserTokens(User user) {
        return revokeAllUserTokens(user, Instant.now());
    }

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :now OR (r.revoked = true AND r.revokedAt < :cutoff)")
    int deleteExpiredAndOldRevokedTokens(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}
