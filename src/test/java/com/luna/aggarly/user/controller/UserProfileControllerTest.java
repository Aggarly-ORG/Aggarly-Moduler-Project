package com.luna.aggarly.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.user.dto.request.ChangePasswordRequest;
import com.luna.aggarly.user.dto.request.UserProfileUpdate;
import com.luna.aggarly.user.dto.request.VerifyPhoneRequest;
import com.luna.aggarly.user.dto.response.UserProfileResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserProfileService userProfileService;

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

    private User mockUser;
    private UUID userId;

    /**
     * addFilters = false disables the whole servlet filter chain (Security included),
     * so the request goes straight to DispatcherServlet -> Controller.
     * That means SecurityMockMvcRequestPostProcessors.user(...) won't work, since it
     * relies on SecurityContextHolderFilter (which is disabled) to propagate the
     * authentication into SecurityContextHolder.
     * Setting SecurityContextHolder directly works because MockMvc runs synchronously
     * on the same thread as this test, so @AuthenticationPrincipal resolves it from
     * the ThreadLocal correctly.
     */
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = User.builder()
                .email("john.doe@example.com")
                .username("johndoe")
                .firstName("John")
                .lastName("Doe")
                .roles(Set.of(Role.builder().name("GUEST").build()))
                .build();
        mockUser.setId(userId);

        UserPrincipal principal = new UserPrincipal(mockUser);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/users/me - Should return current user profile")
    void getMyProfile_ShouldReturn200() throws Exception {
        UserProfileResponse response = UserProfileResponse.builder()
                .id(userId)
                .email("john.doe@example.com")
                .username("johndoe")
                .firstName("John")
                .lastName("Doe")
                .displayName("John Doe")
                .roles(Set.of("GUEST"))
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();

        when(userProfileService.getProfile(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me - Should update and return profile")
    void updateMyProfile_ShouldReturn200() throws Exception {
        UserProfileUpdate request = UserProfileUpdate.builder()
                .firstName("Jonathan")
                .lastName("Doe")
                .bio("Software Engineer and Explorer")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .id(userId)
                .email("john.doe@example.com")
                .username("johndoe")
                .firstName("Jonathan")
                .lastName("Doe")
                .bio("Software Engineer and Explorer")
                .roles(Set.of("GUEST"))
                .build();

        when(userProfileService.updateProfile(eq(userId), any(UserProfileUpdate.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Jonathan"))
                .andExpect(jsonPath("$.data.bio").value("Software Engineer and Explorer"));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/avatar - Should upload avatar file")
    void uploadAvatar_ShouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake image content".getBytes()
        );

        when(userProfileService.uploadAvatar(eq(userId), any())).thenReturn("https://storage.aggarly.com/avatars/user-avatar.png");

        mockMvc.perform(multipart("/api/v1/users/me/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://storage.aggarly.com/avatars/user-avatar.png"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/avatar-url - Should update avatar URL")
    void updateAvatarUrl_ShouldReturn200() throws Exception {
        doNothing().when(userProfileService).updateAvatarUrl(userId, "https://cdn.example.com/avatar.jpg");

        mockMvc.perform(put("/api/v1/users/me/avatar-url")
                        .param("avatarUrl", "https://cdn.example.com/avatar.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me/avatar - Should remove avatar")
    void removeAvatar_ShouldReturn200() throws Exception {
        doNothing().when(userProfileService).removeAvatar(userId);

        mockMvc.perform(delete("/api/v1/users/me/avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/users/me/change-password - Should change password")
    void changePassword_ShouldReturn200() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass123!");
        doNothing().when(userProfileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/phone/send-otp - Should send phone OTP")
    void sendPhoneOtp_ShouldReturn200() throws Exception {
        doNothing().when(userProfileService).sendPhoneOtp(userId);

        mockMvc.perform(post("/api/v1/users/me/phone/send-otp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/phone/verify - Should verify phone OTP")
    void verifyPhone_ShouldReturn200() throws Exception {
        VerifyPhoneRequest request = new VerifyPhoneRequest("123456");
        doNothing().when(userProfileService).verifyPhone(eq(userId), any(VerifyPhoneRequest.class));

        mockMvc.perform(post("/api/v1/users/me/phone/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/become-host - Should upgrade user to HOST")
    void becomeHost_ShouldReturn200() throws Exception {
        doNothing().when(userProfileService).becomeHost(userId);

        mockMvc.perform(post("/api/v1/users/me/become-host"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - Should deactivate user account")
    void deactivateAccount_ShouldReturn200() throws Exception {
        doNothing().when(userProfileService).deactivateAccount(userId);

        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}