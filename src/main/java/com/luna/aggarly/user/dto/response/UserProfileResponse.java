package com.luna.aggarly.user.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        String displayName,
        String phone,
        String avatarUrl,
        String bio,
        Set<String> roles,
        boolean emailVerified,
        boolean phoneVerified,
        boolean mfaEnabled,
        Instant createdAt
) {}
