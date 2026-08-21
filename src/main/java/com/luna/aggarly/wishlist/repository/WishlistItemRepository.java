package com.luna.aggarly.wishlist.repository;

import com.luna.aggarly.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    Optional<WishlistItem> findByWishlistIdAndPropertyId(UUID wishlistId, UUID propertyId);

    boolean existsByWishlistIdAndPropertyId(UUID wishlistId, UUID propertyId);

    List<WishlistItem> findByWishlistIdOrderByCreatedAtDesc(UUID wishlistId);

    long countByWishlistId(UUID wishlistId);
}
