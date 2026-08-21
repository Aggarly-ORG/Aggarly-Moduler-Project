package com.luna.aggarly.wishlist.mapper;

import com.luna.aggarly.wishlist.dto.WishlistItemResponse;
import com.luna.aggarly.wishlist.dto.WishlistResponse;
import com.luna.aggarly.wishlist.entity.Wishlist;
import com.luna.aggarly.wishlist.entity.WishlistItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WishlistMapper {

    default WishlistResponse toResponse(Wishlist wishlist, long itemCount, List<WishlistItemResponse> items) {
        if (wishlist == null) return null;
        return new WishlistResponse(
                wishlist.getId(),
                wishlist.getUserId(),
                wishlist.getName(),
                wishlist.getDescription(),
                wishlist.isPublic(),
                itemCount,
                items,
                wishlist.getCreatedAt()
        );
    }

    default WishlistItemResponse toItemResponse(WishlistItem item, com.luna.aggarly.property.dto.response.PropertyResponse property) {
        if (item == null) return null;
        return new WishlistItemResponse(
                item.getId(),
                item.getWishlistId(),
                item.getPropertyId(),
                property,
                item.getCreatedAt()
        );
    }
}
