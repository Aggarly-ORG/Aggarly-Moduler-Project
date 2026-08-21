package com.luna.aggarly.scheduler.repository;

import com.luna.aggarly.scheduler.entity.ScheduledTaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledTaskExecutionRepository extends JpaRepository<ScheduledTaskExecution, UUID> {

    Page<ScheduledTaskExecution> findByTaskIdOrderByStartedAtDesc(UUID taskId, Pageable pageable);

    @Query("SELECT e FROM ScheduledTaskExecution e WHERE e.task.userId = :userId ORDER BY e.startedAt DESC")
    Page<ScheduledTaskExecution> findByUserIdOrderByStartedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    List<ScheduledTaskExecution> findTop10ByTaskIdOrderByStartedAtDesc(UUID taskId);
}
