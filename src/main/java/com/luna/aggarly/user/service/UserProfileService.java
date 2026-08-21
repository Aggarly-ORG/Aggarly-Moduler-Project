package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.request.ChangePasswordRequest;
import com.luna.aggarly.user.dto.request.UserProfileUpdate;
import com.luna.aggarly.user.dto.request.VerifyPhoneRequest;
import com.luna.aggarly.user.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UserProfileUpdate request);

    String uploadAvatar(UUID userId, MultipartFile file);

    void updateAvatarUrl(UUID userId, String avatarUrl);

    void removeAvatar(UUID userId);

    void changePassword(UUID userId, ChangePasswordRequest request);

    void sendPhoneOtp(UUID userId);

    void verifyPhone(UUID userId, VerifyPhoneRequest request);

    void becomeHost(UUID userId);

    void deactivateAccount(UUID userId);
}
