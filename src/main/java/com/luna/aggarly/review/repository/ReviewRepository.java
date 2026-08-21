package com.luna.aggarly.review.repository;

import com.luna.aggarly.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByBookingId(UUID bookingId);

    Page<Review> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    Page<Review> findByGuestIdOrderByCreatedAtDesc(UUID guestId, Pageable pageable);

    boolean existsByBookingId(UUID bookingId);

    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.propertyId = :propertyId AND r.deleted = false")
    Object[] getRatingSummaryByPropertyId(@Param("propertyId") UUID propertyId);
}
