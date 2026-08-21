package com.luna.aggarly.user.security;

import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.RoleRepository;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.oauth2.OAuth2UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DefaultOAuth2UserService delegate;

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    @Test
    @DisplayName("Should create new user when logging in via Google OAuth2 for the first time")
    void shouldCreateNewUserOnGoogleOAuth2Login() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .userInfoUri("http://localhost/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OAuth2UserRequest request = new OAuth2UserRequest(clientRegistration, accessToken);

        Map<String, Object> attributes = Map.of(
                "sub", "google-12345",
                "email", "googleuser@gmail.com",
                "given_name", "Google",
                "family_name", "User",
                "picture", "http://avatar.com/pic.jpg"
        );
        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "sub"
        );

        when(delegate.loadUser(request)).thenReturn(oAuth2User);

        Role guestRole = Role.builder().id(1L).name("GUEST").build();
        when(userRepository.findByEmail("googleuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findAnyByEmail("googleuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("googleuser")).thenReturn(false);
        when(roleRepository.findByName("GUEST")).thenReturn(Optional.of(guestRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        OAuth2User result = oAuth2UserService.loadUser(request);

        assertThat(result).isNotNull().isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) result;
        assertThat(principal.getUsername()).isEqualTo("googleuser@gmail.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();

        assertThat(createdUser.getEmail()).isEqualTo("googleuser@gmail.com");
        assertThat(createdUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(createdUser.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Should throw OAuth2AuthenticationException if email is missing from provider attributes")
    void shouldThrowExceptionWhenEmailIsMissing() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/callback")
                .authorizationUri("http://localhost/auth")
                .tokenUri("http://localhost/token")
                .userInfoUri("http://localhost/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OAuth2UserRequest request = new OAuth2UserRequest(clientRegistration, accessToken);

        Map<String, Object> attributes = Map.of("sub", "no-email-user");
        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "sub");

        when(delegate.loadUser(request)).thenReturn(oAuth2User);

        assertThatThrownBy(() -> oAuth2UserService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("OAuth2 provider did not return an email address.");
    }
}
