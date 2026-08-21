package com.luna.aggarly.aiagent.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_tool_invocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiToolInvocation extends BaseEntity {

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String parametersJson;

    @Column(columnDefinition = "TEXT")
    private String resultSummaryJson;

    @Column(nullable = false)
    private boolean success;

    private String errorCode;

    @Column(nullable = false)
    private long durationMs;

    private Integer promptTokens;
    private Integer completionTokens;
    private Double estimatedCostUsd;

    @Column(nullable = false)
    private Instant executedAt;
}
