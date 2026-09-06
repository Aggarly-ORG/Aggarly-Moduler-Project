package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.filestorage.dto.response.UploadResponse;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.user.dto.request.ChangePasswordRequest;
import com.luna.aggarly.user.dto.request.UserProfileUpdate;
import com.luna.aggarly.user.dto.request.VerifyPhoneRequest;
import com.luna.aggarly.user.dto.response.UserProfileResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.exceptions.InvalidCredentialsException;
import com.luna.aggarly.user.exceptions.UserNotFoundException;
import com.luna.aggarly.user.exceptions.VerificationException;
import com.luna.aggarly.user.repository.RoleRepository;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.service.OtpService;
import com.luna.aggarly.user.service.UserProfileService;
import com.luna.aggarly.user.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final com.luna.aggarly.user.repository.RefreshTokenRepository refreshTokenRepository;
    private final UserSessionService userSessionService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final com.luna.aggarly.user.mapper.UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = findUserById(userId);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdate request) {
        User user = findUserById(userId);

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.bio() != null) user.setBio(request.bio());

        User saved = userRepository.save(user);
        log.info("Profile updated for user: {}", saved.getEmail());
        return userMapper.toProfileResponse(saved);
    }

    @Override
    @Transactional
    public String uploadAvatar(UUID userId, MultipartFile file) {
        User user = findUserById(userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file cannot be null or empty");
        }

        UploadResponse uploadResponse = fileStorageService.uploadFile(file);
        fileStorageService.markAsActive(uploadResponse.id());
        String avatarUrl = fileStorageService.resolveUrl(uploadResponse.objectKey());

        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        log.info("Avatar uploaded and set for user {}: {}", user.getEmail(), avatarUrl);
        return avatarUrl;
    }

    @Override
    @Transactional
    public void updateAvatarUrl(UUID userId, String avatarUrl) {
        User user = findUserById(userId);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        log.info("Avatar URL updated for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void removeAvatar(UUID userId) {
        User user = findUserById(userId);
        user.setAvatarUrl(null);
        userRepository.save(user);
        log.info("Avatar removed for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new VerificationException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Password changed and sessions revoked for user: {}", user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public void sendPhoneOtp(UUID userId) {
        User user = findUserById(userId);

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new VerificationException("Please register a phone number in your profile before requesting OTP");
        }

        String otpCode = otpService.generatePhoneOtp(user.getId().toString());
        log.info("📱 6-digit OTP sent to phone {} for user {}", user.getPhone(), user.getEmail());
    }

    @Override
    @Transactional
    public void verifyPhone(UUID userId, VerifyPhoneRequest request) {
        User user = findUserById(userId);

        if (user.isPhoneVerified()) {
            throw new VerificationException("Phone is already verified");
        }

        boolean isValid = otpService.validatePhoneOtp(user.getId().toString(), request.otpCode());
        if (!isValid) {
            throw new VerificationException("Invalid or expired 6-digit OTP code");
        }

        user.setPhoneVerified(true);
        userRepository.save(user);
        log.info("✅ Phone verified for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void becomeHost(UUID userId) {
        User user = findUserById(userId);

        if (!user.isEmailVerified()) {
            throw new VerificationException("Email must be verified before becoming a host");
        }

        Role hostRole = roleRepository.findByName("HOST")
                .orElseThrow(() -> new RuntimeException("Default HOST role not seeded in database"));

        if (!user.getRoles().contains(hostRole)) {
            user.getRoles().add(hostRole);
            userRepository.save(user);
            log.info("🏠 User {} upgraded to HOST role", user.getEmail());
        }
    }

    @Override
    @Transactional
    public void deactivateAccount(UUID userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        userSessionService.revokeAllSessions(userId);
        log.info("Account deactivated (soft-deleted) and sessions revoked for user: {}", user.getEmail());
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}
