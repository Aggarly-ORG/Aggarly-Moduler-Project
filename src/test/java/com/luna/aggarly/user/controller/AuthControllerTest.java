package com.luna.aggarly.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.luna.aggarly.user.entity.enums.AuthStatus;
import com.luna.aggarly.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.luna.aggarly.common.security.jwt.JwtService jwtService;

    @MockitoBean
    private com.luna.aggarly.user.security.UserPrincipalDetailsService userPrincipalDetailsService;

    @MockitoBean
    private com.luna.aggarly.user.security.oauth2.OAuth2UserService oAuth2UserService;

    @MockitoBean
    private com.luna.aggarly.user.security.oauth2.OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    private com.luna.aggarly.common.security.handler.DelegatingAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private com.luna.aggarly.common.security.handler.DelegatingAccessDeniedHandler accessDeniedHandler;

    @MockitoBean
    private com.luna.aggarly.common.filters.CorrelationIdFilter correlationIdFilter;

    @MockitoBean
    private com.luna.aggarly.common.security.jwt.JwtAuthenticationFilter jwtAuthFilter;

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 201 CREATED for valid request")
    void register_ShouldReturn201() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john@example.com",
                "Password123!",
                "johndoe",
                "John",
                "Doe",
                "+12025550123",
                null,
                null
        );

        AuthResponse authResponse = AuthResponse.builder()
                .status(AuthStatus.AUTH_SUCCESS)
                .token("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900000)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AUTH_SUCCESS"))
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 200 OK for valid credentials")
    void login_ShouldReturn200() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "Password123!");
        AuthResponse authResponse = AuthResponse.builder()
                .status(AuthStatus.AUTH_SUCCESS)
                .token("access-token")
                .refreshToken("refresh-token")
                .expiresIn(900000)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AUTH_SUCCESS"))
                .andExpect(jsonPath("$.data.token").value("access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - Should return 200 OK")
    void refresh_ShouldReturn200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token", "expired-access-token");
        AuthResponse authResponse = AuthResponse.builder()
                .status(AuthStatus.AUTH_SUCCESS)
                .token("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(900000)
                .build();

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - Should return 200 OK")
    void logout_ShouldReturn200() throws Exception {
        doNothing().when(authService).logout("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .param("refreshToken", "refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/verify-email - Should return 200 OK")
    void verifyEmail_ShouldReturn200() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("john@example.com", "123456");
        doNothing().when(authService).verifyEmail(any(VerifyEmailRequest.class));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/forgot-password - Should return 200 OK")
    void forgotPassword_ShouldReturn200() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("john@example.com");
        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/reset-password - Should return 200 OK")
    void resetPassword_ShouldReturn200() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("john@example.com", "123456", "NewPassword123!");
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/enable-mfa - Should return 200 OK with QR/token")
    void enableMfa_ShouldReturn200() throws Exception {
        RequestMfaResponse response = RequestMfaResponse.builder()
                .qr("data:image/png;base64,iVBORw0KGgo...")
                .token("mfa-token-123")
                .build();

        when(authService.requestMfa()).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/enable-mfa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("mfa-token-123"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/confirm-mfa - Should return 200 OK")
    void confirmMfa_ShouldReturn200() throws Exception {
        ConfirmMfaRequest request = new ConfirmMfaRequest("138949r5","123456");
        doNothing().when(authService).confirmMfa(any(ConfirmMfaRequest.class));

        mockMvc.perform(post("/api/v1/auth/confirm-mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/validate-mfa - Should return 200 OK with token")
    void validateMfa_ShouldReturn200() throws Exception {
        TotpRequest request = new TotpRequest("mfa-token-123", "123456");
        AuthResponse authResponse = AuthResponse.builder()
                .status(AuthStatus.AUTH_SUCCESS)
                .token("authenticated-jwt-token")
                .expiresIn(900000)
                .build();

        when(authService.totpValidate(any(TotpRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/validate-mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("authenticated-jwt-token"));
    }
}
