package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.request.Mfa;
import com.luna.aggarly.user.dto.request.MfaConfirmation;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.entity.enums.AuthStatus;
import com.luna.aggarly.user.service.impl.MfaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MfaServiceImpl mfaService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should create MFA login challenge token and store in Redis")
    void shouldCreateMfaChallengeToken() {
        UUID userId = UUID.randomUUID();

        AuthResponse response = mfaService.create(userId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(AuthStatus.MFA_REQUIRED);
        assertThat(response.token()).isNotNull();
        assertThat(response.expiresIn()).isEqualTo(300L);

        verify(valueOperations, times(1)).set(startsWith("mfa:"), any(Mfa.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Should get MFA challenge entry by token")
    void shouldGetMfaChallengeEntry() {
        String token = "test-mfa-token";
        UUID userId = UUID.randomUUID();
        Mfa mfa = new Mfa(userId, Instant.now());

        when(valueOperations.get("mfa:" + token)).thenReturn(mfa);

        Mfa result = mfaService.get(token);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should create MFA confirmation entry with secret")
    void shouldCreateMfaConfirmationEntry() {
        UUID userId = UUID.randomUUID();
        String secret = "SECRET123";

        String token = mfaService.CreateMfa(userId, secret);

        assertThat(token).isNotNull().isNotBlank();
        verify(valueOperations, times(1)).set(startsWith("mfa:"), any(MfaConfirmation.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Should delete token from Redis")
    void shouldDeleteTokenFromRedis() {
        String token = "token-to-delete";

        mfaService.delete(token);

        verify(redisTemplate, times(1)).delete("mfa:" + token);
    }
}
