package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.request.ConfirmMfaRequest;
import com.luna.aggarly.user.dto.request.ForgotPasswordRequest;
import com.luna.aggarly.user.dto.request.LoginRequest;
import com.luna.aggarly.user.dto.request.RefreshTokenRequest;
import com.luna.aggarly.user.dto.request.RegisterRequest;
import com.luna.aggarly.user.dto.request.ResetPasswordRequest;
import com.luna.aggarly.user.dto.request.TotpRequest;
import com.luna.aggarly.user.dto.request.VerifyEmailRequest;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.dto.response.RequestMfaResponse;
import com.luna.aggarly.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, Registration, Password Recovery & MFA APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ApiResponse.created(response, "User registered successfully").toResponseEntity();
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and issue token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.ok(response, "User authenticated successfully").toResponseEntity();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate expired Access Token using Refresh Token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ApiResponse.ok(response, "Access token refreshed successfully").toResponseEntity();
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate active session refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken) {
        authService.logout(refreshToken);
        return ApiResponse.<Void>empty("Logged out successfully").toResponseEntity();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify user email address using the 6-digit OTP code")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.<Void>empty("Email verified successfully").toResponseEntity();
    }

    @PostMapping("/send-verification")
    @Operation(summary = "Resend 6-digit email verification OTP")
    public ResponseEntity<ApiResponse<Void>> sendVerification(@RequestParam("email") String email) {
        authService.sendVerificationEmail(email);
        return ApiResponse.<Void>empty("6-digit verification OTP resent to your email").toResponseEntity();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a 6-digit password reset OTP code via email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.<Void>empty("If the email exists, a 6-digit OTP has been sent.").toResponseEntity();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the 6-digit OTP code")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.<Void>empty("Password reset successfully. Please log in.").toResponseEntity();
    }

    @PostMapping("/enable-mfa")
    @Operation(summary = "Request MFA QR code setup", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<RequestMfaResponse>> requestMFA() {
        RequestMfaResponse response = authService.requestMfa();
        return ApiResponse.ok(response, "MFA secret generated successfully").toResponseEntity();
    }

    @PostMapping("/confirm-mfa")
    @Operation(summary = "Confirm and activate MFA using 6-digit code", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> confirmMFA(@Valid @RequestBody ConfirmMfaRequest request) {
        authService.confirmMfa(request);
        return ApiResponse.<Void>empty("MFA activated successfully").toResponseEntity();
    }

    @PostMapping("/validate-mfa")
    @Operation(summary = "Validate MFA code during login to complete authentication")
    public ResponseEntity<ApiResponse<AuthResponse>> validateMFA(@Valid @RequestBody TotpRequest request) {
        AuthResponse response = authService.totpValidate(request);
        return ApiResponse.ok(response, "MFA validation successful").toResponseEntity();
    }
}
