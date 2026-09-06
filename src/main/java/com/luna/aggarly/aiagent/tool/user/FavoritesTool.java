package com.luna.aggarly.aiagent.tool.user;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.wishlist.dto.WishlistResponse;
import com.luna.aggarly.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FavoritesTool implements Tool<FavoritesTool.Params, List<WishlistResponse>> {

    public record Params(
            UUID userId
    ) {}

    private final WishlistService wishlistService;

    @Override
    public String name() {
        return "user.favorites";
    }

    @Override
    public String description() {
        return "Fetch wishlists and bookmarked properties for a user.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<List<WishlistResponse>> execute(Params params, UserPrincipal user) {
        UUID targetUserId = (params != null && params.userId() != null)
                ? params.userId()
                : (user != null ? user.getUserId() : null);
        if (targetUserId == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to view favorites.");
        }
        List<WishlistResponse> wishlists = wishlistService.getUserWishlists(targetUserId);
        return ToolResult.ok(wishlists);
    }
}
