package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken,

    String expiredAccessToken
) {}
