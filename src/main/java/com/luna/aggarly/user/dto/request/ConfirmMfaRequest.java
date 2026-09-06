package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmMfaRequest(
        @NotBlank(message = "MFA confirmation token is required")
        String token,

        @NotBlank(message = "TOTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be exactly 6 digits")
        String totpCode
) {
}
