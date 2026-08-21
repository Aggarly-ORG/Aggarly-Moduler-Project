package com.luna.aggarly.aiagent.repository;

import com.luna.aggarly.aiagent.entity.AiSearchContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiSearchContextRepository extends JpaRepository<AiSearchContext, UUID> {
    Optional<AiSearchContext> findByConversationId(UUID conversationId);
}
