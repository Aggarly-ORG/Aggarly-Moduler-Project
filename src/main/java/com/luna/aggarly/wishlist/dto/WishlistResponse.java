package com.luna.aggarly.wishlist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WishlistResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        boolean isPublic,
        String shareToken,
        long itemCount,
        List<WishlistItemResponse> items,
        Instant createdAt
) {
    public WishlistResponse(UUID id, UUID userId, String name, String description, boolean isPublic, long itemCount, List<WishlistItemResponse> items, Instant createdAt) {
        this(id, userId, name, description, isPublic, null, itemCount, items, createdAt);
    }
}
