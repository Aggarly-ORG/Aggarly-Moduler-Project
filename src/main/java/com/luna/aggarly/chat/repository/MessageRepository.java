package com.luna.aggarly.chat.repository;

import com.luna.aggarly.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.createdAt < :beforeTimestamp ORDER BY m.createdAt DESC")
    List<Message> findMessagesBefore(@Param("conversationId") UUID conversationId,
                                     @Param("beforeTimestamp") Instant beforeTimestamp,
                                     Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC")
    List<Message> findRecentMessages(@Param("conversationId") UUID conversationId, Pageable pageable);
}
