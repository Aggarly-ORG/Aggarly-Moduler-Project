package com.luna.aggarly.aiagent.repository;

import com.luna.aggarly.aiagent.entity.AiToolInvocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiToolInvocationRepository extends JpaRepository<AiToolInvocation, UUID> {
    List<AiToolInvocation> findByConversationId(UUID conversationId);
}
