package com.luna.aggarly.user.security;

import com.luna.aggarly.common.security.jwt.JwtService;
import com.luna.aggarly.user.entity.RefreshToken;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.UserSession;
import com.luna.aggarly.user.repository.RefreshTokenRepository;
import com.luna.aggarly.user.security.oauth2.OAuth2SuccessHandler;
import com.luna.aggarly.user.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @DisplayName("Should redirect using URL fragment (#) instead of query parameters (?) to prevent token leakage")
    void shouldRedirectUsingFragmentToPreventTokenLeakage() throws Exception {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        UserPrincipal principal = new UserPrincipal(user);
        when(authentication.getPrincipal()).thenReturn(principal);

        UUID sessionId = UUID.randomUUID();
        UserSession mockSession = UserSession.builder().build();
        ReflectionTestUtils.setField(mockSession, "id", sessionId);
        when(userSessionService.createSession(eq(user), any(UUID.class), eq(request))).thenReturn(mockSession);

        String mockAccessToken = "mock-jwt-access-token";
        when(jwtService.generateToken(principal, sessionId)).thenReturn(mockAccessToken);

        ReflectionTestUtils.setField(oAuth2SuccessHandler, "frontendRedirectUrl", "http://localhost:3000/oauth2/callback");
        ReflectionTestUtils.setField(oAuth2SuccessHandler, "refreshExpirationDays", 7);

        // When
        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        // Then
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getFamilyId()).isNotNull();
        assertThat(savedToken.getAssociatedAccessTokenHash()).isNotBlank();
        assertThat(savedToken.isRevoked()).isFalse();

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        String redirectUrl = redirectCaptor.getValue();

        // Must use fragment (#) and NOT query parameter (?)
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth2/callback#");
        assertThat(redirectUrl).doesNotContain("?");
        assertThat(redirectUrl).contains("token=" + mockAccessToken);
        assertThat(redirectUrl).contains("refreshToken=" + savedToken.getToken());
    }
}
