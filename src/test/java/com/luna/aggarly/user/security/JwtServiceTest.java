package com.luna.aggarly.user.security;

import com.luna.aggarly.common.security.jwt.JwtService;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "base64Secret", "aggarly-dev-secret-key-must-be-at-least-256-bits-long-for-hs256");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900000L); // 15 minutes
        jwtService.init();
    }

    @Test
    @DisplayName("Should generate and validate JWT token successfully")
    void shouldGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("test@example.com")
                .roles(Set.of(Role.builder().name("GUEST").build()))
                .build();
        user.setId(userId);

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should return false for invalid token")
    void shouldReturnFalseForInvalidToken() {
        assertThat(jwtService.validateToken("invalid.jwt.token")).isFalse();
    }
}
