package com.luna.aggarly.user.dto.request;

import com.luna.aggarly.user.utils.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,64}$",
    message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, one special character, and be 8-64 characters long.")
    String password,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    String username,

    @NotBlank(message = "First name is required")
    String firstName,
    @NotBlank(message = "last name is required")
    String lastName,
    @ValidPhone
    String phone,
    String avatarUrl,
    String bio
) {}
