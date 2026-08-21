package com.luna.aggarly.availability.repository;

import com.luna.aggarly.availability.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    @Query(value = """
        SELECT COUNT(*) > 0 FROM availability_slots
        WHERE property_id = :propertyId
          AND available = false
          AND date_range && daterange(cast(:checkIn as date), cast(:checkOut as date), '[)')
        """, nativeQuery = true)
    boolean existsOverlappingBlockedSlot(@Param("propertyId") UUID propertyId,
                                          @Param("checkIn") LocalDate checkIn,
                                          @Param("checkOut") LocalDate checkOut);

    @Query(value = """
        SELECT * FROM availability_slots
        WHERE property_id = :propertyId
          AND date_range && daterange(cast(:from as date), cast(:to as date), '[)')
        ORDER BY start_date
        """, nativeQuery = true)
    List<AvailabilitySlot> findSlotsInRange(@Param("propertyId") UUID propertyId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    Optional<AvailabilitySlot> findByPropertyIdAndBookingId(UUID propertyId, UUID bookingId);
}
