package com.luna.aggarly.user.dto.request;

import lombok.Builder;

@Builder
public record UserProfileUpdate(
    String firstName,
    String lastName,
    String displayName,
    String phone,
    String avatarUrl,
    String bio
) {}
