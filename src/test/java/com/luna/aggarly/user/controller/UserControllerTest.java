package com.luna.aggarly.user.controller;

import com.luna.aggarly.user.dto.response.UserProfileSummaryResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.mapper.UserMapper;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserMapper userMapper;

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

    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        User currentUser = User.builder()
                .email("auth@example.com")
                .username("authuser")
                .firstName("Auth")
                .lastName("User")
                .roles(Set.of(Role.builder().name("GUEST").build()))
                .build();
        currentUser.setId(currentUserId);

        UserPrincipal principal = new UserPrincipal(currentUser);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} - Should return public user profile by ID")
    void getUserById_ShouldReturn200() throws Exception {
        UUID targetId = UUID.randomUUID();
        User targetUser = User.builder()
                .email("jane@example.com")
                .username("janedoe")
                .firstName("Jane")
                .lastName("Doe")
                .build();
        targetUser.setId(targetId);

        UserProfileSummaryResponse summary = new UserProfileSummaryResponse(
                targetId,
                "jane@example.com",
                "Jane",
                "Doe",
                "Jane Doe",
                "janedoe",
                null,
                "Explorer"
        );

        when(userRepository.findById(targetId)).thenReturn(Optional.of(targetUser));
        when(userMapper.toSummaryResponse(targetUser)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/users/{id}", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(targetId.toString()))
                .andExpect(jsonPath("$.data.username").value("janedoe"))
                .andExpect(jsonPath("$.data.firstName").value("Jane"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} - Should return 404 when user not found")
    void getUserById_NotFound_ShouldReturn404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/users/search - Should return users matching search query")
    void searchUsers_WithQuery_ShouldReturnMatchingUsers() throws Exception {
        UUID targetId = UUID.randomUUID();
        User targetUser = User.builder()
                .email("alice@example.com")
                .username("alice")
                .firstName("Alice")
                .lastName("Smith")
                .build();
        targetUser.setId(targetId);

        UserProfileSummaryResponse summary = new UserProfileSummaryResponse(
                targetId,
                "alice@example.com",
                "Alice",
                "Smith",
                "Alice Smith",
                "alice",
                null,
                "Host in Cairo"
        );

        when(userRepository.searchUsers(eq("alice"), any(PageRequest.class))).thenReturn(List.of(targetUser));
        when(userMapper.toSummaryResponse(targetUser)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/users/search")
                        .param("query", "alice")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].firstName").value("Alice"));
    }
}
