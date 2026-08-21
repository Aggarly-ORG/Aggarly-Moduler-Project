package com.luna.aggarly.user.dto.response;

import com.luna.aggarly.user.entity.enums.AuthStatus;
import lombok.Builder;

@Builder
public record AuthResponse(
    AuthStatus status,
    String token,
    String refreshToken,
    long expiresIn
) {
}
