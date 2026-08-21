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

        RefreshToken token1 = RefreshToken.builder()
                .token("ref-token-1")
                .associatedAccessTokenHash("hash1")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        token1.setCreatedAt(Instant.now());

        RefreshToken token2 = RefreshToken.builder()
                .token("ref-token-2")
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
}
