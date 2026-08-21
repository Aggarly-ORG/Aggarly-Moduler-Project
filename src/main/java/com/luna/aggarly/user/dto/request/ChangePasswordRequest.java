package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,64}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, one special character, and be 8-64 characters long.")
    String newPassword
) {}
