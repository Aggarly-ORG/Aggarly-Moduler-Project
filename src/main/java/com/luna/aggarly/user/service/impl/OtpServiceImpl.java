package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing 6-digit numeric OTP codes stored in Redis with short TTL (15 minutes).
 * Includes an in-memory fallback for local dev environments where Redis may be unreachable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String EMAIL_VERIFY_PREFIX = "otp:email_verify:";
    private static final String PASSWORD_RESET_PREFIX = "otp:password_reset:";
    private static final String PHONE_VERIFY_PREFIX = "otp:phone_verify:";

    private static final Duration OTP_EXPIRATION = Duration.ofMinutes(5);

    private final Map<String, String> localMemoryStore = new ConcurrentHashMap<>();

    @Override
    public String generateEmailVerificationOtp(String email) {
        String key = EMAIL_VERIFY_PREFIX + email.toLowerCase().trim();
        return generateAndStoreOtp(key, OTP_EXPIRATION);
    }

    @Override
    public boolean validateEmailVerificationOtp(String email, String code) {
        String key = EMAIL_VERIFY_PREFIX + email.toLowerCase().trim();
        return validateAndConsumeOtp(key, code);
    }

    @Override
    public String generatePasswordResetOtp(String email) {
        String key = PASSWORD_RESET_PREFIX + email.toLowerCase().trim();
        return generateAndStoreOtp(key, OTP_EXPIRATION);
    }

    @Override
    public boolean validatePasswordResetOtp(String email, String code) {
        String key = PASSWORD_RESET_PREFIX + email.toLowerCase().trim();
        return validateAndConsumeOtp(key, code);
    }

    @Override
    public String generatePhoneOtp(String phoneOrUserId) {
        String key = PHONE_VERIFY_PREFIX + phoneOrUserId;
        return generateAndStoreOtp(key, OTP_EXPIRATION);
    }

    @Override
    public boolean validatePhoneOtp(String phoneOrUserId, String code) {
        String key = PHONE_VERIFY_PREFIX + phoneOrUserId;
        return validateAndConsumeOtp(key, code);
    }

    private String generateAndStoreOtp(String key, Duration timeout) {
        int number = secureRandom.nextInt(1_000_000);
        String otp = String.format("%06d", number);

        try {
            redisTemplate.opsForValue().set(key, otp, timeout);
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, falling back to local memory store for OTP: {}", e.getMessage());
            localMemoryStore.put(key, otp);
        }
        return otp;
    }

    private boolean validateAndConsumeOtp(String key, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        String storedOtp = null;
        try {
            storedOtp = redisTemplate.opsForValue().get(key);
            if (storedOtp != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, checking local memory store for OTP: {}", e.getMessage());
            storedOtp = localMemoryStore.remove(key);
        }

        return code.trim().equals(storedOtp);
    }
}
