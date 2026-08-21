package com.luna.aggarly.common.security;

import com.luna.aggarly.user.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Helper utility class to statically access authenticated user context properties.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Prevent instantiation
    }

    /**
     * Gets the current user principal details.
     *
     * @return UserPrincipal, or null if not authenticated
     */
    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * Gets the current user ID.
     *
     * @return User UUID, or null if not authenticated
     */
    public static UUID getCurrentUserId() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getUserId() : null;
    }

    /**
     * Gets the current user email.
     *
     * @return User email, or null if not authenticated
     */
    public static String getCurrentUserEmail() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return principal != null ? principal.getUsername() : null;
    }

    /**
     * Checks if the current user possesses a specific role authority (e.g. GUEST, HOST, ADMIN).
     *
     * @param role name of role
     * @return true if possessed, false otherwise
     */
    public static boolean hasRole(String role) {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            return false;
        }
        String requiredRole = "ROLE_" + role.toUpperCase();
        return principal.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(requiredRole));
    }
}
