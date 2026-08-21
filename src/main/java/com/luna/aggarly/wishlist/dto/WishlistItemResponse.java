package com.luna.aggarly.wishlist.dto;

import com.luna.aggarly.property.dto.response.PropertyResponse;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        UUID wishlistId,
        UUID propertyId,
        PropertyResponse property,
        Instant addedAt
) {
}
