package com.luna.aggarly.user.security;

import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return nulls and false when SecurityContext is unauthenticated")
    void shouldReturnNullAndFalseWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.getCurrentUserPrincipal()).isNull();
        assertThat(SecurityUtils.getCurrentUserId()).isNull();
        assertThat(SecurityUtils.getCurrentUserEmail()).isNull();
        assertThat(SecurityUtils.hasRole("GUEST")).isFalse();
    }

    @Test
    @DisplayName("Should correctly resolve user principal details and roles when authenticated")
    void shouldResolveAuthenticatedUserDetails() {
        UUID userId = UUID.randomUUID();
        Role guestRole = Role.builder().id(1L).name("GUEST").build();
        User user = User.builder()
                .email("test@example.com")
                .username("testuser")
                .roles(Set.of(guestRole))
                .build();
        user.setId(userId);

        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.getCurrentUserPrincipal()).isEqualTo(principal);
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(userId);
        assertThat(SecurityUtils.getCurrentUserEmail()).isEqualTo("test@example.com");
        assertThat(SecurityUtils.hasRole("GUEST")).isTrue();
        assertThat(SecurityUtils.hasRole("HOST")).isFalse();
    }
}
