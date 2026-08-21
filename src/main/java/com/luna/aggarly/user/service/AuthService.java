package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.request.*;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.dto.response.RequestMfaResponse;
import com.luna.aggarly.user.entity.User;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    AuthResponse totpValidate(TotpRequest request);
    RequestMfaResponse requestMfa();
    void confirmMfa(ConfirmMfaRequest request);
    void logout(String refreshToken);
    UserProfileUpdate getCurrentUserProfile();
    void updateCurrentUserProfile(UserProfileUpdate request);
    void verifyEmail(VerifyEmailRequest request);
    void sendVerificationEmail(String email);
    void changePassword(ChangePasswordRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void sendPhoneOtp();
    void verifyPhone(VerifyPhoneRequest request);
    void becomeHost();
    void deactivateAccount();
}
