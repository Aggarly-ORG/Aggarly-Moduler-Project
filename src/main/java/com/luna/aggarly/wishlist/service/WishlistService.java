package com.luna.aggarly.wishlist.service;

import com.luna.aggarly.wishlist.dto.*;

import java.util.List;
import java.util.UUID;

public interface WishlistService {

    WishlistResponse createWishlist(CreateWishlistRequest request, UUID userId);

    List<WishlistResponse> getUserWishlists(UUID userId);

    WishlistResponse getWishlistById(UUID wishlistId, UUID userId);

    WishlistResponse updateWishlist(UUID wishlistId, UpdateWishlistRequest request, UUID userId);

    void deleteWishlist(UUID wishlistId, UUID userId);

    WishlistItemResponse addPropertyToWishlist(UUID wishlistId, UUID propertyId, UUID userId);

    void removePropertyFromWishlist(UUID wishlistId, UUID propertyId, UUID userId);

    boolean isPropertyInUserWishlist(UUID userId, UUID propertyId);
}
