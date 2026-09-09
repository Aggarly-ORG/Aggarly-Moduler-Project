package com.luna.aggarly.booking.repository;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndGuestId(UUID id, UUID guestId);

    List<Booking> findByGuestIdOrderByCheckInDesc(UUID guestId);

    List<Booking> findByHostIdOrderByCheckInDesc(UUID hostId);

    List<Booking> findByStatusAndCheckOutBefore(BookingStatus status, LocalDate date);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoffTime);

    List<Booking> findByStatusAndCheckInLessThanEqualAndCheckOutGreaterThanEqual(BookingStatus status, LocalDate checkIn, LocalDate checkOut);

    long countByStatus(BookingStatus status);

    boolean existsByGuestIdAndPropertyIdAndStatus(UUID guestId, UUID propertyId, BookingStatus status);
}
