package com.luna.aggarly.user.security.oauth2;

import com.luna.aggarly.common.security.jwt.JwtService;
import com.luna.aggarly.user.entity.RefreshToken;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.UserSession;
import com.luna.aggarly.user.repository.RefreshTokenRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.user.service.UserSessionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Success Handler invoked after successful OAuth2 social authentication.
 * Generates Access and Refresh tokens, stores them, and redirects the client to the frontend callback.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionService userSessionService;

    @Value("${app.jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    @Value("${app.oauth2.frontend-redirect-url:http://localhost:3000/oauth2/callback}")
    private String frontendRedirectUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
        User user = userDetails.getUser();

        // Create active user device session
        UUID familyId = UUID.randomUUID();
        UserSession session = userSessionService.createSession(user, familyId, request);
        UUID sessionId = session != null ? session.getId() : null;

        // Generate Access and Refresh tokens with dedicated session familyId and sessionId
        String accessToken = jwtService.generateToken(userDetails, sessionId);
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .familyId(familyId)
                .associatedAccessTokenHash(hashToken(accessToken))
                .user(user)
                .expiryDate(Instant.now().plus(Duration.ofDays(refreshExpirationDays)))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // Redirect to Frontend URL passing tokens via URL fragment (#) so tokens are never leaked to server access logs or Referer headers
        String redirectUrl = String.format("%s#token=%s&refreshToken=%s",
                frontendRedirectUrl, accessToken, refreshTokenValue);

        response.sendRedirect(redirectUrl);
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
