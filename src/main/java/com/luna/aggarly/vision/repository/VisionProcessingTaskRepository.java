package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VisionProcessingTaskRepository extends JpaRepository<VisionProcessingTask, UUID> {

    List<VisionProcessingTask> findByStatusOrderByPriorityAscScheduledAtAsc(VisionTaskStatus status, Pageable pageable);

    List<VisionProcessingTask> findByPropertyImageIdAndStatusIn(UUID propertyImageId, List<VisionTaskStatus> statuses);

    List<VisionProcessingTask> findByPropertyId(UUID propertyId);

    List<VisionProcessingTask> findByStatus(VisionTaskStatus status);

    List<VisionProcessingTask> findByStatusIn(List<VisionTaskStatus> statuses);

    long countByStatus(VisionTaskStatus status);

    @Modifying
    @Query("DELETE FROM VisionProcessingTask t WHERE t.status = :status")
    int deleteTasksByStatus(@Param("status") VisionTaskStatus status);

    @Modifying
    @Query("UPDATE VisionProcessingTask t SET t.status = 'PROCESSING', t.workerId = :workerId, t.workerInstanceId = :workerInstanceId, t.leaseUntil = :leaseUntil, t.heartbeatAt = :heartbeatAt, t.startedAt = :startedAt WHERE t.id = :taskId AND t.status = 'QUEUED'")
    int claimTask(@Param("taskId") UUID taskId,
                  @Param("workerId") String workerId,
                  @Param("workerInstanceId") String workerInstanceId,
                  @Param("leaseUntil") Instant leaseUntil,
                  @Param("heartbeatAt") Instant heartbeatAt,
                  @Param("startedAt") Instant startedAt);

    @Modifying
    @Query("UPDATE VisionProcessingTask t SET t.leaseUntil = :newLeaseUntil, t.heartbeatAt = :heartbeatAt WHERE t.id = :taskId AND t.workerInstanceId = :workerInstanceId AND t.status = 'PROCESSING'")
    int renewLease(@Param("taskId") UUID taskId,
                   @Param("workerInstanceId") String workerInstanceId,
                   @Param("newLeaseUntil") Instant newLeaseUntil,
                   @Param("heartbeatAt") Instant heartbeatAt);

    @Modifying
    @Query("UPDATE VisionProcessingTask t SET t.status = 'QUEUED', t.attemptCount = t.attemptCount + 1, t.workerId = NULL, t.workerInstanceId = NULL, t.leaseUntil = NULL, t.heartbeatAt = NULL, t.scheduledAt = :rescheduleAt WHERE t.status = 'PROCESSING' AND t.leaseUntil < :now")
    int recoverExpiredLeases(@Param("now") Instant now, @Param("rescheduleAt") Instant rescheduleAt);
}
