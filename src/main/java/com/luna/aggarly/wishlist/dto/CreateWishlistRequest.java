package com.luna.aggarly.wishlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWishlistRequest(
        @NotBlank(message = "Wishlist name is required")
        @Size(max = 100, message = "Wishlist name cannot exceed 100 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        boolean isPublic
) {
}
