package com.luna.aggarly.aiagent.tool.user;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.wishlist.dto.CreateWishlistRequest;
import com.luna.aggarly.wishlist.dto.WishlistItemResponse;
import com.luna.aggarly.wishlist.dto.WishlistResponse;
import com.luna.aggarly.wishlist.exceptions.DuplicateWishlistItemException;
import com.luna.aggarly.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateWishlistTool implements Tool<UpdateWishlistTool.Params, Map<String, Object>> {

    private static final String DEFAULT_WISHLIST_NAME = "Saved Properties";

    private final WishlistService wishlistService;
    private final JsonSchemaService jsonSchemaService;

    public record Params(
            @JsonPropertyDescription("Action to perform: ADD (save property to a wishlist) or REMOVE (remove it).")
            String action,

            @JsonPropertyDescription("UUID of the property to add or remove.")
            UUID propertyId,

            @JsonPropertyDescription("Optional UUID of the target wishlist. When omitted, the guest's default " +
                    "wishlist is used (created automatically for ADD if none exists).")
            UUID wishlistId
    ) {}

    @Override
    public String name() {
        return "user.wishlist.update";
    }

    @Override
    public String description() {
        return "Save or unsave a property on behalf of the authenticated guest. ADD stores the property in their " +
                "wishlist (default wishlist created automatically when needed); REMOVE takes it out. Use whenever a guest " +
                "says 'save this', 'add to favorites' or 'remove from my list'.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        if (user == null || user.getUserId() == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to manage wishlists.");
        }
        if (params.propertyId() == null) {
            return ToolResult.failed("MISSING_PROPERTY", "A propertyId is required.");
        }
        if (params.action() == null || params.action().isBlank()) {
            return ToolResult.failed("MISSING_ACTION", "Action must be ADD or REMOVE.");
        }

        UUID userId = user.getUserId();
        String action = params.action().trim().toUpperCase();

        try {
            return switch (action) {
                case "ADD" -> addToWishlist(params, userId);
                case "REMOVE" -> removeFromWishlist(params, userId);
                default -> ToolResult.failed("INVALID_ACTION",
                        "Unknown action '" + params.action() + "'. Use ADD or REMOVE.");
            };
        } catch (DuplicateWishlistItemException ex) {
            return ToolResult.ok(Map.of(
                    "action", "ADD",
                    "propertyId", params.propertyId(),
                    "alreadySaved", true,
                    "message", "This property is already in your wishlist."
            ));
        } catch (Exception ex) {
            log.error("Wishlist update failed for user {} property {}", userId, params.propertyId(), ex);
            return ToolResult.failed("WISHLIST_UPDATE_FAILED", ex.getMessage());
        }
    }

    private ToolResult<Map<String, Object>> addToWishlist(Params params, UUID userId) {
        UUID targetId = resolveTargetWishlist(params.wishlistId(), userId, true);
        WishlistItemResponse item = wishlistService.addPropertyToWishlist(targetId, params.propertyId(), userId);
        WishlistResponse wishlist = wishlistService.getWishlistById(targetId, userId);

        return ToolResult.ok(Map.of(
                "action", "ADD",
                "propertyId", params.propertyId(),
                "wishlistId", targetId,
                "wishlistName", wishlist.name(),
                "itemCount", wishlist.itemCount(),
                "message", "Property saved to '" + wishlist.name() + "'."
        ));
    }

    private ToolResult<Map<String, Object>> removeFromWishlist(Params params, UUID userId) {
        UUID targetId = locateWishlistContaining(params.wishlistId(), params.propertyId(), userId);
        if (targetId == null) {
            return ToolResult.ok(Map.of(
                    "action", "REMOVE",
                    "propertyId", params.propertyId(),
                    "wasSaved", false,
                    "message", "That property was not found in any of your wishlists."
            ));
        }
        wishlistService.removePropertyFromWishlist(targetId, params.propertyId(), userId);
        return ToolResult.ok(Map.of(
                "action", "REMOVE",
                "propertyId", params.propertyId(),
                "wishlistId", targetId,
                "message", "Property removed from your wishlist."
        ));
    }

    private UUID resolveTargetWishlist(UUID requested, UUID userId, boolean createIfMissing) {
        if (requested != null) {
            return requested;
        }
        List<WishlistResponse> wishlists = wishlistService.getUserWishlists(userId);
        if (!wishlists.isEmpty()) {
            return wishlists.get(0).id();
        }
        if (!createIfMissing) {
            return null;
        }
        WishlistResponse created = wishlistService.createWishlist(
                new CreateWishlistRequest(DEFAULT_WISHLIST_NAME, "Properties saved by the AI concierge.", false),
                userId
        );
        return created.id();
    }

    private UUID locateWishlistContaining(UUID requested, UUID propertyId, UUID userId) {
        if (requested != null) {
            return requested;
        }
        List<WishlistResponse> wishlists = wishlistService.getUserWishlists(userId);
        for (WishlistResponse wishlist : wishlists) {
            boolean contains = wishlist.items() != null && wishlist.items().stream()
                    .anyMatch(item -> propertyId.equals(item.propertyId()));
            if (contains) {
                return wishlist.id();
            }
        }
        return wishlists.isEmpty() ? null : wishlists.get(0).id();
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
