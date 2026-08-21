package com.luna.aggarly.aiagent.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_user_memories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUserMemory extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String memoryKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String memoryValue;

    @Column(nullable = false)
    private boolean userConfirmed;

    public String getMemoryKey() { return memoryKey; }
    public String getMemoryValue() { return memoryValue; }
    public UUID getUserId() { return userId; }
    public boolean isUserConfirmed() { return userConfirmed; }
}
