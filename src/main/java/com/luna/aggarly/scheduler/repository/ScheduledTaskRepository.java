package com.luna.aggarly.scheduler.repository;

import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
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
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, UUID> {

    @Query(value = """
        SELECT * FROM scheduled_task
        WHERE status = 'ACTIVE' 
          AND next_execution_at IS NOT NULL 
          AND next_execution_at <= :now
        ORDER BY next_execution_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<ScheduledTask> claimDueTasksForUpdate(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
        UPDATE scheduled_task
        SET status = 'RUNNING', last_started_at = :now
        WHERE id = :taskId AND status = 'ACTIVE'
    """, nativeQuery = true)
    int markTaskAsRunningIfActive(@Param("taskId") UUID taskId, @Param("now") Instant now);

    Page<ScheduledTask> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<ScheduledTask> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, TaskStatus status, Pageable pageable);

    Optional<ScheduledTask> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, TaskStatus status);
}
