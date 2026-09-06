package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UserProfileUpdate(
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    String displayName,

    @Size(max = 25, message = "Phone number must not exceed 25 characters")
    String phone,

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    String avatarUrl,

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    String bio
) {}
