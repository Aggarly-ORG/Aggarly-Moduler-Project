package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TotpRequest(
        @NotBlank(message = "token is required")
        String token,
        @NotBlank(message = "totp code is required")
        String totpCode
) {
}
