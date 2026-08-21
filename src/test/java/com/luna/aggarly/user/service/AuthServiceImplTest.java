package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.request.*;
import com.luna.aggarly.user.dto.response.AuthResponse;
import com.luna.aggarly.user.entity.enums.AuthStatus;
import com.luna.aggarly.user.dto.response.RequestMfaResponse;
import com.luna.aggarly.user.entity.*;
import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.user.exceptions.*;
import com.luna.aggarly.user.repository.RefreshTokenRepository;
import com.luna.aggarly.user.repository.RoleRepository;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.common.security.jwt.JwtService;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.impl.AuthServiceImpl;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private MfaService mfaService;

    @Mock
    private GoogleAuthenticator googleAuthenticator;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private Role guestRole;
    private Role hostRole;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 900000L);
        ReflectionTestUtils.setField(authService, "refreshExpirationDays", 7);

        userId = UUID.randomUUID();
        guestRole = Role.builder().id(1L).name("GUEST").build();
        hostRole = Role.builder().id(2L).name("HOST").build();

        testUser = User.builder()
                .email("user@example.com")
                .passwordHash("encodedPassword")
                .username("user123")
                .firstName("John")
                .lastName("Doe")
                .phone("+12025550123")
                .emailVerified(true)
                .phoneVerified(true)
                .identityVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .roles(new HashSet<>(List.of(guestRole)))
                .build();
        testUser.setId(userId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateSecurityContextUser() {
        UserPrincipal principal = new UserPrincipal(testUser);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Should successfully register a new user and issue tokens")
    void shouldRegisterNewUserSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                "newuser@example.com",
                "password123",
                "newuser",
                "Jane",
                "Doe",
                "+12025550199",
                "http://avatar.com/pic.jpg",
                "Hello bio"
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(roleRepository.findByName("GUEST")).thenReturn(Optional.of(guestRole));
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(otpService.generateEmailVerificationOtp(anyString())).thenReturn("123456");
        when(jwtService.generateToken(any(User.class))).thenReturn("access-jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(AuthStatus.AUTH_SUCCESS);
        assertThat(response.token()).isEqualTo("access-jwt-token");
        assertThat(response.refreshToken()).isNotNull();

        verify(userRepository, times(1)).save(any(User.class));
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when email is already registered")
    void shouldThrowExceptionWhenRegisteringExistingEmail() {
        RegisterRequest request = new RegisterRequest(
                "user@example.com", "password", "user123", "F", "L", null, null, null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when username is taken")
    void shouldThrowExceptionWhenRegisteringExistingUsername() {
        RegisterRequest request = new RegisterRequest(
                "unique@example.com", "password", "user123", "F", "L", null, null, null
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Username already in use");
    }

    @Test
    @DisplayName("Should login successfully and return tokens when credentials are valid")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UserPrincipal principal = new UserPrincipal(testUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateToken(testUser)).thenReturn("access-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(AuthStatus.AUTH_SUCCESS);
        assertThat(response.token()).isEqualTo("access-jwt-token");
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(testUser);
    }

    @Test
    @DisplayName("Should return MFA_REQUIRED when logging in user with MFA enabled")
    void shouldRequireMfaOnLoginIfEnabled() {
        testUser.setMfaEnabled(true);
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UserPrincipal principal = new UserPrincipal(testUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        AuthResponse mfaResponse = AuthResponse.builder().status(AuthStatus.MFA_REQUIRED).token("mfa-token").build();
        when(mfaService.create(userId)).thenReturn(mfaResponse);

        AuthResponse response = authService.login(request);

        assertThat(response.status()).isEqualTo(AuthStatus.MFA_REQUIRED);
        assertThat(response.token()).isEqualTo("mfa-token");
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException on bad password")
    void shouldThrowInvalidCredentialsOnBadPassword() {
        LoginRequest request = new LoginRequest("user@example.com", "wrongpass");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should refresh access token when valid refresh token is provided")
    void shouldRefreshTokenSuccessfully() {
        String oldRefreshTokenVal = "old-refresh-token";
        String expiredAccessToken = "expired-access-token";

        String hash = ReflectionTestUtils.invokeMethod(authService, "hashToken", expiredAccessToken);

        RefreshToken oldToken = RefreshToken.builder()
                .token(oldRefreshTokenVal)
                .associatedAccessTokenHash(hash)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshTokenVal, expiredAccessToken);

        when(refreshTokenRepository.findByToken(oldRefreshTokenVal)).thenReturn(Optional.of(oldToken));
        when(jwtService.generateToken(testUser)).thenReturn("new-access-jwt");

        AuthResponse response = authService.refresh(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("new-access-jwt");
        assertThat(oldToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("Should revoke all tokens and throw exception if refresh token is expired or revoked")
    void shouldRevokeAllTokensWhenRefreshTokenExpired() {
        String oldRefreshTokenVal = "expired-refresh-token";
        String expiredAccessToken = "expired-access-token";
        String hash = ReflectionTestUtils.invokeMethod(authService, "hashToken", expiredAccessToken);

        RefreshToken oldToken = RefreshToken.builder()
                .token(oldRefreshTokenVal)
                .associatedAccessTokenHash(hash)
                .user(testUser)
                .expiryDate(Instant.now().minusSeconds(3600)) // expired
                .revoked(false)
                .build();

        RefreshTokenRequest request = new RefreshTokenRequest(oldRefreshTokenVal, "expired-access-token");
        when(refreshTokenRepository.findByToken(oldRefreshTokenVal)).thenReturn(Optional.of(oldToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Refresh token is expired or has been reused.");

        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(testUser);
    }

    @Test
    @DisplayName("Should verify email with valid OTP")
    void shouldVerifyEmailWithValidOtp() {
        testUser.setEmailVerified(false);
        VerifyEmailRequest request = new VerifyEmailRequest("user@example.com", "123456");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(otpService.validateEmailVerificationOtp("user@example.com", "123456")).thenReturn(true);

        authService.verifyEmail(request);

        assertThat(testUser.isEmailVerified()).isTrue();
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should throw VerificationException when verifying already verified email")
    void shouldThrowExceptionWhenVerifyingAlreadyVerifiedEmail() {
        testUser.setEmailVerified(true);
        VerifyEmailRequest request = new VerifyEmailRequest("user@example.com", "123456");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.verifyEmail(request))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("Email is already verified");
    }

    @Test
    @DisplayName("Should upgrade user to HOST when all verifications pass")
    void shouldBecomeHostSuccessfully() {
        authenticateSecurityContextUser();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByName("HOST")).thenReturn(Optional.of(hostRole));

        authService.becomeHost();

        assertThat(testUser.getRoles()).contains(hostRole);
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should throw VerificationException when trying to become host without email verification")
    void shouldFailBecomeHostWithoutEmailVerification() {
        testUser.setEmailVerified(false);
        authenticateSecurityContextUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.becomeHost())
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("Email must be verified before becoming a host");
    }

    @Test
    @DisplayName("Should soft delete account on deactivate")
    void shouldDeactivateAccount() {
        authenticateSecurityContextUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        authService.deactivateAccount();

        assertThat(testUser.isDeleted()).isTrue();
        verify(userRepository, times(1)).save(testUser);
        verify(refreshTokenRepository, times(1)).revokeAllUserTokens(testUser);
    }

    @Test
    @DisplayName("Should request MFA setup successfully")
    void shouldRequestMfaSuccessfully() {
        authenticateSecurityContextUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        testUser.setMfaEnabled(false);

        GoogleAuthenticatorKey key = new GoogleAuthenticatorKey.Builder("SECRETKEY123").build();
        when(googleAuthenticator.createCredentials()).thenReturn(key);
        when(mfaService.CreateMfa(eq(userId), anyString())).thenReturn("mfa-setup-token");

        RequestMfaResponse response = authService.requestMfa();

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mfa-setup-token");
    }
}
