package com.luna.aggarly.user.security.oauth2;

import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.RoleRepository;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Service to load OAuth2 user info, check if the user exists locally,
 * and dynamically provision them with default GUEST credentials if missing.
 */
@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DefaultOAuth2UserService delegate;

    @Autowired
    public OAuth2UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this(userRepository, roleRepository, new DefaultOAuth2UserService());
    }

    public OAuth2UserService(UserRepository userRepository, RoleRepository roleRepository, DefaultOAuth2UserService delegate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String providerName = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        AuthProvider authProvider = AuthProvider.valueOf(providerName);

        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = extractEmail(attributes, authProvider);
        if (email == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "invalid_token",
                            "OAuth2 provider did not return an email address.",
                            null
                    )
            );
        }

        String name = extractName(attributes, authProvider);
        String firstName = extractFirstName(attributes, authProvider);
        String lastName = extractLastName(attributes, authProvider);
        String avatarUrl = extractAvatarUrl(attributes, authProvider);
        String username = extractUsername(attributes, authProvider, email);

        // Find existing user or create a new one
        User user = userRepository.findByEmail(email).orElseGet(() -> {

            User deletedUser = userRepository.findAnyByEmail(email).orElse(null);

            if (deletedUser != null) {
                deletedUser.setDeleted(false);
                return deletedUser;
            }
            String finalUsername = username;
            if (userRepository.existsByUsername(finalUsername)) {
                finalUsername = finalUsername + "-" + UUID.randomUUID().toString().substring(0, 5);
            }

            // Fetch default GUEST role
            Role guestRole = roleRepository.findByName("GUEST")
                    .orElseThrow(() -> new RuntimeException("Default role GUEST not found in database"));

            User newUser = User.builder()
                    .username(finalUsername)
                    .email(email)
                    .authProvider(authProvider)
                    .displayName(name)
                    .firstName(firstName)
                    .lastName(lastName)
                    .avatarUrl(avatarUrl)
                    .emailVerified(true)
                    .roles(Collections.singleton(guestRole))
                    .build();
            return userRepository.save(newUser);
        });

        // Update profile details if missing
        boolean updated = false;
        if (user.getDisplayName() == null && name != null) { user.setDisplayName(name); updated = true; }
        if (user.getFirstName() == null && firstName != null) { user.setFirstName(firstName); updated = true; }
        if (user.getLastName() == null && lastName != null) { user.setLastName(lastName); updated = true; }
        if (user.getAvatarUrl() == null && avatarUrl != null) { user.setAvatarUrl(avatarUrl); updated = true; }
        if (updated) {
            userRepository.save(user);
        }

        return new UserPrincipal(user, attributes);
    }

    private String extractEmail(Map<String, Object> attributes, AuthProvider provider) {
        if (provider == AuthProvider.GITHUB) {
            Object email = attributes.get("email");
            if (email != null) return email.toString();
            Object login = attributes.get("login");
            if (login != null) return login.toString() + "@github.local";
        }
        Object emailObj = attributes.get("email");
        return emailObj != null ? emailObj.toString() : null;
    }

    private String extractName(Map<String, Object> attributes, AuthProvider provider) {
        Object name = attributes.get("name");
        if (name != null) return name.toString();
        if (provider == AuthProvider.GITHUB) {
            Object login = attributes.get("login");
            if (login != null) return login.toString();
        }
        return null;
    }

    private String extractFirstName(Map<String, Object> attributes, AuthProvider provider) {
        if (provider == AuthProvider.GOOGLE && attributes.get("given_name") != null) {
            return attributes.get("given_name").toString();
        }
        String name = extractName(attributes, provider);
        return name != null ? name.split(" ")[0] : null;
    }

    private String extractLastName(Map<String, Object> attributes, AuthProvider provider) {
        if (provider == AuthProvider.GOOGLE && attributes.get("family_name") != null) {
            return attributes.get("family_name").toString();
        }
        String name = extractName(attributes, provider);
        if (name != null && name.contains(" ")) {
            return name.substring(name.indexOf(" ") + 1);
        }
        return null;
    }

    private String extractAvatarUrl(Map<String, Object> attributes, AuthProvider provider) {
        if (provider == AuthProvider.GOOGLE && attributes.get("picture") != null) {
            return attributes.get("picture").toString();
        } else if (provider == AuthProvider.GITHUB && attributes.get("avatar_url") != null) {
            return attributes.get("avatar_url").toString();
        }
        return null;
    }

    private String extractUsername(Map<String, Object> attributes, AuthProvider provider, String email) {
        if (provider == AuthProvider.GITHUB && attributes.get("login") != null) {
            return attributes.get("login").toString();
        }
        if (email == null) {
            return "user-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return email.split("@")[0].replaceAll("[^a-zA-Z0-9-]", "");
    }
}
