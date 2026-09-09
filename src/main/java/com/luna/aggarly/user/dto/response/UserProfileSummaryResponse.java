package com.luna.aggarly.user.dto.response;

import java.util.UUID;

public record UserProfileSummaryResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String username,
        String avatarUrl,
        String bio,
        java.util.Set<String> roles
) {
    public UserProfileSummaryResponse(
            UUID id, String email, String firstName, String lastName,
            String displayName, String username, String avatarUrl, String bio
    ) {
        this(id, email, firstName, lastName, displayName, username, avatarUrl, bio, java.util.Set.of("GUEST"));
    }
}
