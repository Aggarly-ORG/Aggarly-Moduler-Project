package com.luna.aggarly.user.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.dto.request.ChangePasswordRequest;
import com.luna.aggarly.user.dto.request.UserProfileUpdate;
import com.luna.aggarly.user.dto.request.VerifyPhoneRequest;
import com.luna.aggarly.user.dto.response.UserProfileResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Current User Profile, Avatar Uploads & Account Settings APIs")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        UserProfileResponse response = userProfileService.getProfile(userId);
        return ApiResponse.ok(response, "Profile retrieved successfully").toResponseEntity();
    }

    @PutMapping
    @Operation(summary = "Update current user profile information", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileUpdate request) {
        UUID userId = principal.getUserId();
        UserProfileResponse response = userProfileService.updateProfile(userId, request);
        return ApiResponse.ok(response, "Profile updated successfully").toResponseEntity();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and update profile avatar photo", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        UUID userId = principal.getUserId();
        String avatarUrl = userProfileService.uploadAvatar(userId, file);
        return ApiResponse.ok(Map.of("avatarUrl", avatarUrl), "Avatar photo uploaded successfully").toResponseEntity();
    }

    @PutMapping("/avatar-url")
    @Operation(summary = "Update profile avatar by direct URL reference", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> updateAvatarUrl(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("avatarUrl") String avatarUrl) {
        UUID userId = principal.getUserId();
        userProfileService.updateAvatarUrl(userId, avatarUrl);
        return ApiResponse.<Void>empty("Avatar URL updated successfully").toResponseEntity();
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Remove profile avatar photo", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> removeAvatar(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        userProfileService.removeAvatar(userId);
        return ApiResponse.<Void>empty("Avatar photo removed successfully").toResponseEntity();
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password (requires current password confirmation)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = principal.getUserId();
        userProfileService.changePassword(userId, request);
        return ApiResponse.<Void>empty("Password changed successfully").toResponseEntity();
    }

    @PostMapping("/phone/send-otp")
    @Operation(summary = "Send 6-digit OTP verification code to registered phone", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> sendPhoneOtp(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        userProfileService.sendPhoneOtp(userId);
        return ApiResponse.<Void>empty("6-digit OTP sent to your phone number").toResponseEntity();
    }

    @PostMapping("/phone/verify")
    @Operation(summary = "Verify phone number using 6-digit OTP code", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> verifyPhone(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VerifyPhoneRequest request) {
        UUID userId = principal.getUserId();
        userProfileService.verifyPhone(userId, request);
        return ApiResponse.<Void>empty("Phone number verified successfully").toResponseEntity();
    }

    @PostMapping("/become-host")
    @Operation(summary = "Upgrade account to HOST role", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> becomeHost(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        userProfileService.becomeHost(userId);
        return ApiResponse.<Void>empty("You are now upgraded to a Host! You can start listing properties.").toResponseEntity();
    }

    @DeleteMapping
    @Operation(summary = "Deactivate (soft-delete) your account", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUserId();
        userProfileService.deactivateAccount(userId);
        return ApiResponse.<Void>empty("Account deactivated successfully").toResponseEntity();
    }
}
