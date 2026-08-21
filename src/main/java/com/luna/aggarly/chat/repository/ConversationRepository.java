package com.luna.aggarly.chat.repository;

import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.userId = :userId AND p.archived = false ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findUserActiveConversations(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM Conversation c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.type = :type AND p1.userId = :user1Id AND p2.userId = :user2Id")
    Optional<Conversation> findDirectConversationBetween(@Param("user1Id") UUID user1Id,
                                                         @Param("user2Id") UUID user2Id,
                                                         @Param("type") ConversationType type);

    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE c.type = 'AI_CONCIERGE' AND p.userId = :userId")
    Optional<Conversation> findAiConciergeConversation(@Param("userId") UUID userId);

    Optional<Conversation> findByBookingId(UUID bookingId);

    Optional<Conversation> findByAiConversationId(UUID aiConversationId);
}
