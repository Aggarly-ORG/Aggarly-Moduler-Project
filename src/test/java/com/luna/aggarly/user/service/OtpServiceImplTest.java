package com.luna.aggarly.user.service;

import com.luna.aggarly.user.service.impl.OtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should generate 6-digit OTP and store in Redis for email verification")
    void shouldGenerateEmailVerificationOtp() {
        String email = "test@example.com";

        String otp = otpService.generateEmailVerificationOtp(email);

        assertThat(otp).isNotNull().hasSize(6).matches("\\d{6}");
        verify(valueOperations, times(1)).set(eq("otp:email_verify:" + email), eq(otp), any(Duration.class));
    }

    @Test
    @DisplayName("Should validate and consume valid email verification OTP")
    void shouldValidateAndConsumeEmailOtp() {
        String email = "test@example.com";
        String otp = "123456";
        String key = "otp:email_verify:" + email;

        when(valueOperations.get(key)).thenReturn(otp);

        boolean isValid = otpService.validateEmailVerificationOtp(email, otp);

        assertThat(isValid).isTrue();
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    @DisplayName("Should return false when validating incorrect or missing OTP without consuming OTP")
    void shouldReturnFalseForIncorrectOtp() {
        String email = "test@example.com";
        String key = "otp:email_verify:" + email;

        when(valueOperations.get(key)).thenReturn("654321");

        boolean isValid = otpService.validateEmailVerificationOtp(email, "123456");

        assertThat(isValid).isFalse();
        verify(redisTemplate, never()).delete(key);

        boolean isValidBlank = otpService.validateEmailVerificationOtp(email, "");
        assertThat(isValidBlank).isFalse();
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    @DisplayName("Should allow user to correct typo: fail first time without deleting, succeed second time with correct code")
    void shouldAllowCorrectionAfterIncorrectOtp() {
        String email = "retry@example.com";
        String key = "otp:password_reset:" + email;
        String correctOtp = "889900";

        when(valueOperations.get(key)).thenReturn(correctOtp);

        // 1st attempt: typo
        boolean firstAttempt = otpService.validatePasswordResetOtp(email, "112233");
        assertThat(firstAttempt).isFalse();
        verify(redisTemplate, never()).delete(key);

        // 2nd attempt: corrected to right code
        boolean secondAttempt = otpService.validatePasswordResetOtp(email, "889900");
        assertThat(secondAttempt).isTrue();
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    @DisplayName("Should fallback to local memory store when Redis throws exception")
    void shouldFallbackToLocalMemoryStoreWhenRedisFails() {
        String email = "fallback@example.com";
        String key = "otp:email_verify:" + email;

        doThrow(new RuntimeException("Redis connection error")).when(valueOperations).set(eq(key), anyString(), any(Duration.class));
        doThrow(new RuntimeException("Redis connection error")).when(valueOperations).get(key);

        String otp = otpService.generateEmailVerificationOtp(email);
        assertThat(otp).isNotNull().hasSize(6);

        boolean isValid = otpService.validateEmailVerificationOtp(email, otp);
        assertThat(isValid).isTrue();
    }
}
