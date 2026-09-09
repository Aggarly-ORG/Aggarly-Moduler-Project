package com.luna.aggarly.user.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserAdminResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String displayName,
    String username,
    String avatarUrl,
    String bio,
    Set<String> roles,
    String status,
    String kycTier,
    String kycStatus,
    Integer trustScore,
    Instant createdAt
) {}
