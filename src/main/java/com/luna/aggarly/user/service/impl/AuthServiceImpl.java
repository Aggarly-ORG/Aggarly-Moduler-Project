package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.dto.request.*;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.entity.enums.AuthStatus;
import com.luna.aggarly.user.dto.response.RequestMfaResponse;
import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.common.security.jwt.JwtService;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.entity.*;
import com.luna.aggarly.user.exceptions.*;
import com.luna.aggarly.user.repository.*;
import com.luna.aggarly.user.service.AuthService;
import com.luna.aggarly.user.service.EmailService;
import com.luna.aggarly.user.service.MfaService;
import com.luna.aggarly.user.service.OtpService;
import com.luna.aggarly.user.service.UserSessionService;
import com.luna.aggarly.user.utils.QrCodeGenerator;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.aerogear.security.otp.Totp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final MfaService mfaService;
    private final GoogleAuthenticator googleAuthenticator;
    private final EmailService emailService;
    private final UserSessionService userSessionService;

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long jwtExpiration;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use: " + request.email());
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new EmailAlreadyExistsException("Username already in use: " + request.username());
        }

        Role guestRole = roleRepository.findByName("GUEST")
                .orElseThrow(() -> new RuntimeException("Default GUEST role not seeded in database"));

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .avatarUrl(request.avatarUrl())
                .bio(request.bio())
                .emailVerified(false)
                .authProvider(AuthProvider.LOCAL)
                .roles(new HashSet<>(Collections.singletonList(guestRole)))
                .build();

        user = userRepository.save(user);

        // Generate 6-digit numeric OTP stored in Redis (15 min TTL)
        String otpCode = otpService.generateEmailVerificationOtp(user.getEmail());
        log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
        User user = userDetails.getUser();

        if (user.isMfaEnabled()) {
            return mfaService.create(user.getId());
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse totpValidate(TotpRequest request) {

        Mfa otpEntry = mfaService.get(request.token());

        if (otpEntry == null) {
            throw new InvalidMfaTokenException("Invalid Mfa Token");
        }

        User user = userRepository.findById(otpEntry.userId())
                .orElseThrow();

        Totp totp = new Totp(user.getTotpSecret());

        if (!totp.verify(request.totpCode())) {
            throw new InvalidTotpException("The Totp Code is Wrong");
        }

        mfaService.delete(request.token());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public RequestMfaResponse requestMfa(){
        User user = getAuthenticatedUser();
        if(user.isMfaEnabled()) {
            throw new VerificationException("MFA is already enabled for this account");
        }

        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();

        String otpUri = GoogleAuthenticatorQRGenerator.
                getOtpAuthTotpURL("Aggarly",user.getEmail(),key);

        String secret = key.getKey();
        String token = mfaService.CreateMfa(user.getId(),secret);
        try {
            String Qr = QrCodeGenerator.generateBase64(otpUri);
            return RequestMfaResponse.builder().qr(Qr).token(token).build();
        }
        catch (Exception ex){
            return RequestMfaResponse.builder().uri(otpUri).token(token).build();
        }
    }

    @Override
    @Transactional
    public void confirmMfa(ConfirmMfaRequest request){
        MfaConfirmation mfaConfirmation = mfaService.getMfaConfirm(request.token());
        Totp totp = new Totp(mfaConfirmation.secret());

        if (!totp.verify(request.totpCode())) {
            throw new InvalidTotpException("The Totp Code is Wrong");
        }
        User user = userRepository.findById(mfaConfirmation.userId()).orElse(null);
        mfaService.delete(request.token());
        if(user == null)
            throw new UserNotFoundException("user not found");
        user.setMfaEnabled(true);
        user.setTotpSecret(mfaConfirmation.secret());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        // Revoked Token Detection & 15-second Concurrency Mercy Window
        if (oldToken.isRevoked()) {
            Instant revokedAt = oldToken.getRevokedAt() != null ? oldToken.getRevokedAt() : oldToken.getUpdatedAt();
            long secondsSinceRevocation = revokedAt != null
                    ? Math.max(0, Duration.between(revokedAt, Instant.now()).toSeconds())
                    : 9999L;

            if (secondsSinceRevocation <= 15) {
                log.warn("⚠️ Refresh token replay within 15s grace window ({}s elapsed) for family {}. Returning active successor.",
                        secondsSinceRevocation, oldToken.getFamilyId());

                RefreshToken activeToken = null;
                if (oldToken.getReplacedByToken() != null) {
                    activeToken = refreshTokenRepository.findByToken(oldToken.getReplacedByToken()).orElse(null);
                }
                if (activeToken == null || activeToken.isRevoked()) {
                    activeToken = refreshTokenRepository.findFirstByFamilyIdAndRevokedFalse(oldToken.getFamilyId()).orElse(null);
                }

                if (activeToken != null) {
                    UUID sessionId = userSessionService.getSessionIdByFamilyId(oldToken.getFamilyId());
                    String freshAccessToken = sessionId != null
                            ? jwtService.generateToken(oldToken.getUser(), sessionId)
                            : jwtService.generateToken(oldToken.getUser());
                    activeToken.setAssociatedAccessTokenHash(hashToken(freshAccessToken));
                    refreshTokenRepository.save(activeToken);

                    return AuthResponse.builder()
                            .status(AuthStatus.AUTH_SUCCESS)
                            .token(freshAccessToken)
                            .refreshToken(activeToken.getToken())
                            .expiresIn(jwtExpiration)
                            .build();
                }
            }

            // Outside 15s window: Security breach / token theft replay attack detected!
            log.error("🚨 Potential refresh token theft detected! Revoked token reused after {}s. Revoking entire family {}",
                    secondsSinceRevocation, oldToken.getFamilyId());
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), Instant.now());
            userSessionService.revokeSessionByFamilyId(oldToken.getFamilyId());
            throw new InvalidRefreshTokenException("Compromised session detected. Token family revoked. Please log in again.");
        }

        if (oldToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), Instant.now());
            userSessionService.revokeSessionByFamilyId(oldToken.getFamilyId());
            throw new InvalidRefreshTokenException("Refresh token is expired. Please log in again.");
        }

        // Optional check for expiredAccessToken if provided by client
        if (request.expiredAccessToken() != null && !request.expiredAccessToken().isBlank()) {
            String expiredAccessTokenHash = hashToken(request.expiredAccessToken());
            if (oldToken.getAssociatedAccessTokenHash() != null &&
                    !expiredAccessTokenHash.equals(oldToken.getAssociatedAccessTokenHash())) {
                log.warn("Access token hash mismatch during refresh for family {}. Revoking family.", oldToken.getFamilyId());
                refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), Instant.now());
                userSessionService.revokeSessionByFamilyId(oldToken.getFamilyId());
                throw new InvalidRefreshTokenException("Session binding failed. Security token revoked.");
            }
        }

        // Normal Rotation: Rotate token within the same family
        User user = oldToken.getUser();
        userSessionService.updateActivity(oldToken.getFamilyId());
        UUID sessionId = userSessionService.getSessionIdByFamilyId(oldToken.getFamilyId());
        String newAccessToken = sessionId != null
                ? jwtService.generateToken(user, sessionId)
                : jwtService.generateToken(user);
        String newRefreshTokenValue = UUID.randomUUID().toString();
        Instant now = Instant.now();

        oldToken.setRevoked(true);
        oldToken.setRevokedAt(now);
        oldToken.setReplacedByToken(newRefreshTokenValue);
        refreshTokenRepository.save(oldToken);

        RefreshToken newToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .familyId(oldToken.getFamilyId()) // Maintain same family lineage
                .associatedAccessTokenHash(hashToken(newAccessToken))
                .user(user)
                .expiryDate(now.plus(Duration.ofDays(refreshExpirationDays)))
                .revoked(false)
                .revokedAt(null)
                .replacedByToken(null)
                .build();
        refreshTokenRepository.save(newToken);

        return AuthResponse.builder()
                .status(AuthStatus.AUTH_SUCCESS)
                .token(newAccessToken)
                .refreshToken(newRefreshTokenValue)
                .expiresIn(jwtExpiration)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
        userSessionService.revokeSessionByFamilyId(token.getFamilyId());
        log.info("🚪 Session family {} logged out successfully", token.getFamilyId());
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileUpdate getCurrentUserProfile() {
        User user = getAuthenticatedUser();
        return UserProfileUpdate.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .build();
    }

    @Override
    @Transactional
    public void updateCurrentUserProfile(UserProfileUpdate request) {
        User user = getAuthenticatedUser();

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.bio() != null) user.setBio(request.bio());

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("User with email not found: " + request.email()));

        if (user.isEmailVerified()) {
            throw new VerificationException("Email is already verified");
        }

        boolean isValid = otpService.validateEmailVerificationOtp(request.email(), request.otpCode());
        if (!isValid) {
            throw new VerificationException("Invalid or expired 6-digit OTP code");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("✅ Email verified for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void sendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User with email not found: " + email));

        if (user.isEmailVerified()) {
            throw new VerificationException("Email is already verified");
        }

        String otpCode = otpService.generateEmailVerificationOtp(email);

        String html = emailService.otpTemplate(otpCode);

        try {
            emailService.send(email, "Email Verification", html);
            log.info("Sent 6-digit Email verification OTP for {}", email);
        }catch (MessagingException ex){
            log.error("Failed to send 6-digit Email verification OTP for {}", email);
        }
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new VerificationException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("🔑 Password changed for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        var optionalUser = userRepository.findByEmail(request.email());
        if (optionalUser.isEmpty()) {
            log.warn("⚠️ Forgot password requested for unknown email: {}", request.email());
            return;
        }

        User user = optionalUser.get();
        String otpCode = otpService.generatePasswordResetOtp(user.getEmail());
        String html = emailService.otpTemplate(otpCode);

        try {
            emailService.send(request.email(), "Email Verification", html);
            log.info("Sent 6-digit verification OTP for {}", request.email());
        }catch (MessagingException ex){
            log.error("Failed to send 6-digit verification OTP for {}", request.email());
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("User with email not found: " + request.email()));

        boolean isValid = otpService.validatePasswordResetOtp(request.email(), request.otpCode());
        if (!isValid) {
            throw new VerificationException("Invalid or expired 6-digit OTP code");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("🔑 Password reset completed for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void sendPhoneOtp() {
        User user = getAuthenticatedUser();

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new VerificationException("No phone number on file. Update your profile first.");
        }

        if (user.isPhoneVerified()) {
            throw new VerificationException("Phone is already verified");
        }

        String otpCode = otpService.generatePhoneOtp(user.getId().toString());
        log.info("📱 6-digit OTP sent to phone {} for user {}: {}", user.getPhone(), user.getEmail(), otpCode);
    }

    @Override
    @Transactional
    public void verifyPhone(VerifyPhoneRequest request) {
        User user = getAuthenticatedUser();

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
    public void becomeHost() {
        User user = getAuthenticatedUser();

        if (!user.isEmailVerified()) {
            throw new VerificationException("Email must be verified before becoming a host");
        }

//        if (!user.isPhoneVerified()) {
//            throw new VerificationException("Phone must be verified before becoming a host");
//        }
//
//        if (!user.isIdentityVerified()) {
//            throw new VerificationException("Identity must be verified before becoming a host. Please contact support.");
//        }

        Role hostRole = roleRepository.findByName("HOST")
                .orElseThrow(() -> new RuntimeException("Default HOST role not seeded in database"));

        if (!user.getRoles().contains(hostRole)) {
            user.getRoles().add(hostRole);
            userRepository.save(user);
            log.info("🏠 User {} is now a HOST", user.getEmail());
        }
    }


    @Override
    @Transactional
    public void deactivateAccount() {
        User user = getAuthenticatedUser();
        user.setDeleted(true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        userSessionService.revokeAllSessions(user.getId());
        log.info("🗑️ Account deactivated for user: {}", user.getEmail());
    }

    @Transactional(readOnly = true)
    private User getAuthenticatedUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new InvalidCredentialsException("Not authenticated");
        }
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private AuthResponse issueTokens(User user) {
        return issueTokens(user, UUID.randomUUID());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private AuthResponse issueTokens(User user, UUID familyId) {
        UUID effectiveFamilyId = familyId != null ? familyId : UUID.randomUUID();
        UserSession session = userSessionService.createSession(user, effectiveFamilyId, getCurrentHttpRequest());
        UUID sessionId = session != null ? session.getId() : null;
        String accessToken = sessionId != null
                ? jwtService.generateToken(user, sessionId)
                : jwtService.generateToken(user);
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .familyId(effectiveFamilyId)
                .associatedAccessTokenHash(hashToken(accessToken))
                .user(user)
                .expiryDate(Instant.now().plus(Duration.ofDays(refreshExpirationDays)))
                .revoked(false)
                .revokedAt(null)
                .replacedByToken(null)
                .build();
        refreshTokenRepository.save(refreshToken);
        return AuthResponse.builder()
                    .status(AuthStatus.AUTH_SUCCESS)
                    .token(accessToken)
                    .refreshToken(refreshTokenValue)
                    .expiresIn(jwtExpiration)
                    .build();
    }

    private HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
        if (attribs instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String hashToken(String token) {
        if (token == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
