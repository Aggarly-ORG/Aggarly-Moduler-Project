package com.luna.aggarly.scheduler.repository;

import com.luna.aggarly.scheduler.entity.ScheduledEventTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledEventTriggerRepository extends JpaRepository<ScheduledEventTrigger, UUID> {

    @Query("SELECT t FROM ScheduledEventTrigger t JOIN FETCH t.task WHERE t.eventType = :eventType AND t.task.status = 'ACTIVE'")
    List<ScheduledEventTrigger> findActiveByEventType(@Param("eventType") String eventType);

    List<ScheduledEventTrigger> findByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}
