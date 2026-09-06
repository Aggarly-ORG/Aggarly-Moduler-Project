package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.user.entity.RefreshToken;
import com.luna.aggarly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("Should find refresh token by token string and revoke all tokens for user")
    void shouldFindTokenAndRevokeAllForUser() {
        User user = User.builder()
                .email("tokenuser@example.com")
                .username("tokenuser")
                .passwordHash("password")
                .authProvider(AuthProvider.LOCAL)
                .build();
        user.setCreatedAt(Instant.now());
        user = entityManager.persistAndFlush(user);

        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();

        RefreshToken token1 = RefreshToken.builder()
                .token("ref-token-1")
                .familyId(family1)
                .associatedAccessTokenHash("hash1")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        token1.setCreatedAt(Instant.now());

        RefreshToken token2 = RefreshToken.builder()
                .token("ref-token-2")
                .familyId(family2)
                .associatedAccessTokenHash("hash2")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        token2.setCreatedAt(Instant.now());

        entityManager.persist(token1);
        entityManager.persist(token2);
        entityManager.flush();

        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken("ref-token-1");
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getUser().getEmail()).isEqualTo("tokenuser@example.com");

        int updatedCount = refreshTokenRepository.revokeAllUserTokens(user);
        assertThat(updatedCount).isEqualTo(2);

        entityManager.clear();

        assertThat(refreshTokenRepository.findByToken("ref-token-1").get().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByToken("ref-token-2").get().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should revoke single family without affecting other families")
    void shouldRevokeOnlyTargetFamily() {
        User user = User.builder()
                .email("familyuser@example.com")
                .username("familyuser")
                .passwordHash("password")
                .authProvider(AuthProvider.LOCAL)
                .build();
        user.setCreatedAt(Instant.now());
        user = entityManager.persistAndFlush(user);

        UUID familyA = UUID.randomUUID();
        UUID familyB = UUID.randomUUID();

        RefreshToken tokenA = RefreshToken.builder()
                .token("token-fam-a")
                .familyId(familyA)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        tokenA.setCreatedAt(Instant.now());

        RefreshToken tokenB = RefreshToken.builder()
                .token("token-fam-b")
                .familyId(familyB)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        tokenB.setCreatedAt(Instant.now());

        entityManager.persist(tokenA);
        entityManager.persist(tokenB);
        entityManager.flush();

        // Revoke family A only
        int revokedCount = refreshTokenRepository.revokeFamily(familyA, Instant.now());
        assertThat(revokedCount).isEqualTo(1);

        entityManager.clear();

        assertThat(refreshTokenRepository.findByToken("token-fam-a").get().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByToken("token-fam-b").get().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Should delete expired tokens and old revoked tokens")
    void shouldDeleteExpiredAndOldRevokedTokens() {
        User user = User.builder()
                .email("cleanupuser@example.com")
                .username("cleanupuser")
                .passwordHash("password")
                .authProvider(AuthProvider.LOCAL)
                .build();
        user.setCreatedAt(Instant.now());
        user = entityManager.persistAndFlush(user);

        UUID family = UUID.randomUUID();

        // Expired token
        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired-token")
                .familyId(family)
                .user(user)
                .expiryDate(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();
        expiredToken.setCreatedAt(Instant.now().minusSeconds(7200));

        // Old revoked token (revoked 10 days ago)
        RefreshToken oldRevokedToken = RefreshToken.builder()
                .token("old-revoked-token")
                .familyId(family)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(86400))
                .revoked(true)
                .revokedAt(Instant.now().minusSeconds(86400 * 10))
                .build();
        oldRevokedToken.setCreatedAt(Instant.now().minusSeconds(86400 * 10));

        // Active valid token
        RefreshToken activeToken = RefreshToken.builder()
                .token("active-token")
                .familyId(family)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build();
        activeToken.setCreatedAt(Instant.now());

        entityManager.persist(expiredToken);
        entityManager.persist(oldRevokedToken);
        entityManager.persist(activeToken);
        entityManager.flush();

        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(86400 * 7); // 7 days ago

        int deleted = refreshTokenRepository.deleteExpiredAndOldRevokedTokens(now, cutoff);
        assertThat(deleted).isEqualTo(2);

        entityManager.clear();

        assertThat(refreshTokenRepository.findByToken("expired-token")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("old-revoked-token")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("active-token")).isPresent();
    }
}
