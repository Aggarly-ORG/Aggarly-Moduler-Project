package com.luna.aggarly.wishlist.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.wishlist.dto.AddPropertyToWishlistRequest;
import com.luna.aggarly.wishlist.dto.CreateWishlistRequest;
import com.luna.aggarly.wishlist.dto.UpdateWishlistRequest;
import com.luna.aggarly.wishlist.dto.WishlistItemResponse;
import com.luna.aggarly.wishlist.dto.WishlistResponse;
import com.luna.aggarly.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing personal guest wishlists and saved property bookmarks.
 */
@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
@Tag(name = "Wishlists", description = "Saved Property Lists & Bookmarking APIs")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping
    @Operation(summary = "Create a new wishlist collection", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistResponse>> createWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWishlistRequest request) {
        WishlistResponse response = wishlistService.createWishlist(request, principal.getUserId());
        return ApiResponse.created(response, "Wishlist created successfully").toResponseEntity();
    }

    @GetMapping
    @Operation(summary = "Get all wishlists owned by the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<WishlistResponse>>> getMyWishlists(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<WishlistResponse> wishlists = wishlistService.getUserWishlists(principal.getUserId());
        return ApiResponse.ok(wishlists, "Wishlists retrieved successfully").toResponseEntity();
    }

    @GetMapping("/{wishlistId}")
    @Operation(summary = "Get wishlist details by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlistById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId) {
        UUID userId = (principal != null) ? principal.getUserId() : null;
        WishlistResponse response = wishlistService.getWishlistById(wishlistId, userId);
        return ApiResponse.ok(response, "Wishlist retrieved successfully").toResponseEntity();
    }

    @PutMapping("/{wishlistId}")
    @Operation(summary = "Update wishlist name or description", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistResponse>> updateWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId,
            @Valid @RequestBody UpdateWishlistRequest request) {
        WishlistResponse response = wishlistService.updateWishlist(wishlistId, request, principal.getUserId());
        return ApiResponse.ok(response, "Wishlist updated successfully").toResponseEntity();
    }

    @PutMapping("/{wishlistId}/rename")
    @Operation(summary = "Rename wishlist collection title and description", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistResponse>> renameWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId,
            @Valid @RequestBody UpdateWishlistRequest request) {
        WishlistResponse response = wishlistService.updateWishlist(wishlistId, request, principal.getUserId());
        return ApiResponse.ok(response, "Wishlist renamed successfully").toResponseEntity();
    }

    @PostMapping("/{wishlistId}/share")
    @Operation(summary = "Generate an unguessable private shared-board link token", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistResponse>> shareWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId) {
        WishlistResponse response = wishlistService.generateShareToken(wishlistId, principal.getUserId());
        return ApiResponse.ok(response, "Share token generated successfully").toResponseEntity();
    }

    @GetMapping("/shared/{shareToken}")
    @Operation(summary = "Retrieve a shared wishlist collection by token (Public)")
    public ResponseEntity<ApiResponse<WishlistResponse>> getSharedWishlist(@PathVariable String shareToken) {
        WishlistResponse response = wishlistService.getWishlistByShareToken(shareToken);
        return ApiResponse.ok(response, "Shared wishlist retrieved successfully").toResponseEntity();
    }

    @DeleteMapping("/{wishlistId}")
    @Operation(summary = "Delete a wishlist collection", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId) {
        wishlistService.deleteWishlist(wishlistId, principal.getUserId());
        return ApiResponse.<Void>empty("Wishlist deleted successfully").toResponseEntity();
    }

    @PostMapping(value = {"/{wishlistId}/properties", "/{wishlistId}/items"})
    @Operation(summary = "Add a property bookmark to a wishlist", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addPropertyToWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId,
            @Valid @RequestBody AddPropertyToWishlistRequest request) {
        WishlistItemResponse response = wishlistService.addPropertyToWishlist(wishlistId, request.propertyId(), principal.getUserId());
        return ApiResponse.created(response, "Property added to wishlist").toResponseEntity();
    }

    @DeleteMapping(value = {"/{wishlistId}/properties/{propertyId}", "/{wishlistId}/items/{propertyId}"})
    @Operation(summary = "Remove a property bookmark from a wishlist", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> removePropertyFromWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID wishlistId,
            @PathVariable UUID propertyId) {
        wishlistService.removePropertyFromWishlist(wishlistId, propertyId, principal.getUserId());
        return ApiResponse.<Void>empty("Property removed from wishlist").toResponseEntity();
    }

    @DeleteMapping("/properties/{propertyId}")
    @Operation(summary = "Remove a property bookmark from all wishlists of the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> removePropertyFromAllWishlists(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        wishlistService.removePropertyFromAllUserWishlists(principal.getUserId(), propertyId);
        return ApiResponse.<Void>empty("Property removed from wishlists").toResponseEntity();
    }

    @GetMapping("/check")
    @Operation(summary = "Check if a property is in any wishlist of the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Boolean>> isPropertyInWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID propertyId) {
        boolean inWishlist = wishlistService.isPropertyInUserWishlist(principal.getUserId(), propertyId);
        return ApiResponse.ok(inWishlist, "Wishlist status checked").toResponseEntity();
    }
}
