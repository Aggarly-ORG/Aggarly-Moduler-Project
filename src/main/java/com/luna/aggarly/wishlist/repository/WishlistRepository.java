package com.luna.aggarly.wishlist.repository;

import com.luna.aggarly.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    Optional<Wishlist> findByIdAndUserId(UUID id, UUID userId);

    List<Wishlist> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Wishlist> findByShareToken(String shareToken);
}
