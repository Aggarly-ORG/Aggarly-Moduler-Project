package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.dto.request.Mfa;
import com.luna.aggarly.user.dto.request.MfaConfirmation;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.entity.enums.AuthStatus;
import com.luna.aggarly.user.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    public AuthResponse create(UUID userId) {

        String token = UUID.randomUUID().toString();

        Mfa challenge =
                new Mfa(userId, Instant.now());

        redisTemplate.opsForValue()
                .set("mfa:" + token, challenge, EXPIRATION);

        return AuthResponse.builder().status(AuthStatus.MFA_REQUIRED).
                token(token).expiresIn(Duration.ofMinutes(5).toSeconds()).build();
    }
    public Mfa get(String token) {
        Object obj = redisTemplate.opsForValue().get("mfa:" + token);
        if (obj instanceof Mfa mfa) {
            return mfa;
        }
        return null;
    }

    public void delete(String token) {
        redisTemplate.delete("mfa:" + token);
    }

    public String CreateMfa(UUID userId, String secret) {
        String token = UUID.randomUUID().toString();

        MfaConfirmation challenge =
                new MfaConfirmation(userId, secret, Instant.now());

        redisTemplate.opsForValue()
                .set("mfa:" + token, challenge, EXPIRATION);

        return token;
    }

    public MfaConfirmation getMfaConfirm(String token) {
        Object obj = redisTemplate.opsForValue().get("mfa:" + token);
        if (obj instanceof MfaConfirmation mfaConfirm) {
            return mfaConfirm;
        }
        return null;
    }
}