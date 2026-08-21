package com.luna.aggarly.scheduler.repository;

import com.luna.aggarly.scheduler.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT * FROM outbox_event 
        WHERE status = 'PENDING' 
        ORDER BY created_at ASC 
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> claimPendingEventsForPublishing(@Param("limit") int limit);

    long countByStatus(String status);
}
