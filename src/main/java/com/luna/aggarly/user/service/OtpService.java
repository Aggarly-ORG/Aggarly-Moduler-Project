package com.luna.aggarly.user.service;

public interface OtpService {
    String generateEmailVerificationOtp(String email);
    boolean validateEmailVerificationOtp(String email, String code);

    String generatePasswordResetOtp(String email);
    boolean validatePasswordResetOtp(String email, String code);

    String generatePhoneOtp(String phoneOrUserId);
    boolean validatePhoneOtp(String phoneOrUserId, String code);
}
