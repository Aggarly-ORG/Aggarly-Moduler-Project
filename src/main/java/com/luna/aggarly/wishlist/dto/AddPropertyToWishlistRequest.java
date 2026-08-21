package com.luna.aggarly.wishlist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddPropertyToWishlistRequest(
        @NotNull(message = "Property ID is required")
        UUID propertyId
) {
}
