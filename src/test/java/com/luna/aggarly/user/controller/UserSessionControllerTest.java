package com.luna.aggarly.user.controller;

import com.luna.aggarly.user.dto.response.UserSessionResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSessionService userSessionService;

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
    private UUID currentSessionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        currentSessionId = UUID.randomUUID();
        mockUser = User.builder()
                .email("test@example.com")
                .roles(Set.of(Role.builder().name("GUEST").build()))
                .build();
        mockUser.setId(userId);

        UserPrincipal principal = new UserPrincipal(mockUser);
        principal.setSessionId(currentSessionId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/users/me/sessions should return 200 with active session list")
    void getActiveSessions_ShouldReturn200() throws Exception {
        UserSessionResponse session1 = new UserSessionResponse(
                currentSessionId,
                "Chrome on Windows 10/11",
                "127.0.0.1",
                "Localhost / Dev",
                Instant.now(),
                true
        );

        when(userSessionService.getActiveSessions(eq(userId), eq(currentSessionId)))
                .thenReturn(List.of(session1));

        mockMvc.perform(get("/api/v1/users/me/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(currentSessionId.toString()))
                .andExpect(jsonPath("$.data[0].deviceName").value("Chrome on Windows 10/11"))
                .andExpect(jsonPath("$.data[0].isCurrent").value(true));

        verify(userSessionService).getActiveSessions(eq(userId), eq(currentSessionId));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me/sessions/{id} should revoke specific session and return 200")
    void revokeSession_ShouldReturn200() throws Exception {
        UUID targetSessionId = UUID.randomUUID();
        doNothing().when(userSessionService).revokeSession(userId, targetSessionId);

        mockMvc.perform(delete("/api/v1/users/me/sessions/{id}", targetSessionId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session revoked successfully"));

        verify(userSessionService).revokeSession(userId, targetSessionId);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me/sessions should revoke all other sessions and return 200")
    void revokeAllOtherSessions_ShouldReturn200() throws Exception {
        doNothing().when(userSessionService).revokeAllOtherSessions(userId, currentSessionId);

        mockMvc.perform(delete("/api/v1/users/me/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("All other active sessions revoked successfully"));

        verify(userSessionService).revokeAllOtherSessions(userId, currentSessionId);
    }
}
