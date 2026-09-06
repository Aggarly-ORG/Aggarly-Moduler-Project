package com.luna.aggarly.aiagent.repository;

import com.luna.aggarly.aiagent.entity.AiUserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiUserMemoryRepository extends JpaRepository<AiUserMemory, UUID> {
    List<AiUserMemory> findByUserIdAndUserConfirmedTrue(UUID userId);
    List<AiUserMemory> findByUserId(UUID userId);
    Optional<AiUserMemory> findByUserIdAndMemoryKey(UUID userId, String memoryKey);
    Optional<AiUserMemory> findByUserIdAndMemoryKeyIgnoreCase(UUID userId, String memoryKey);
}
