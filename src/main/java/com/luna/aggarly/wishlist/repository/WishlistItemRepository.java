package com.luna.aggarly.wishlist.repository;

import com.luna.aggarly.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying
    @Query(value = "DELETE FROM wishlist_items WHERE wishlist_id = :wishlistId AND property_id = :propertyId", nativeQuery = true)
    void hardDeleteByWishlistIdAndPropertyId(@Param("wishlistId") UUID wishlistId, @Param("propertyId") UUID propertyId);

    @Modifying
    @Query(value = "DELETE FROM wishlist_items WHERE property_id = :propertyId AND wishlist_id IN (SELECT id FROM wishlists WHERE user_id = :userId)", nativeQuery = true)
    void hardDeleteByPropertyIdAndUserId(@Param("propertyId") UUID propertyId, @Param("userId") UUID userId);
}
