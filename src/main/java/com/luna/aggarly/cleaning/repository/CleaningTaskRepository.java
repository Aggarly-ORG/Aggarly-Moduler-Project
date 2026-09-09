package com.luna.aggarly.cleaning.repository;

import com.luna.aggarly.cleaning.entity.CleaningTask;
import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CleaningTaskRepository extends JpaRepository<CleaningTask, UUID> {

    Page<CleaningTask> findByHostIdOrderByScheduledDateDesc(UUID hostId, Pageable pageable);

    List<CleaningTask> findByHostIdOrderByScheduledDateDesc(UUID hostId);

    Page<CleaningTask> findByAssignedCleanerIdOrderByScheduledDateDesc(UUID assignedCleanerId, Pageable pageable);

    Page<CleaningTask> findByPropertyIdOrderByScheduledDateDesc(UUID propertyId, Pageable pageable);

    Page<CleaningTask> findByStatusOrderByScheduledDateAsc(CleaningStatus status, Pageable pageable);

    Optional<CleaningTask> findByBookingId(UUID bookingId);

    List<CleaningTask> findByPropertyIdAndScheduledDate(UUID propertyId, LocalDate scheduledDate);

    @Query("SELECT ct FROM CleaningTask ct WHERE ct.hostId = :hostId AND (:status IS NULL OR ct.status = :status) AND (:propertyId IS NULL OR ct.propertyId = :propertyId)")
    Page<CleaningTask> findByHostFilter(@Param("hostId") UUID hostId,
                                        @Param("status") CleaningStatus status,
                                        @Param("propertyId") UUID propertyId,
                                        Pageable pageable);

    @Query("SELECT ct FROM CleaningTask ct WHERE ct.assignedCleanerId = :cleanerId AND (:status IS NULL OR ct.status = :status)")
    Page<CleaningTask> findByCleanerFilter(@Param("cleanerId") UUID cleanerId,
                                           @Param("status") CleaningStatus status,
                                           Pageable pageable);
}
