package com.luna.aggarly.aiagent.dto;

import java.time.Instant;
import java.util.UUID;

public record ToolCallLogResponse(
        UUID id,
        UUID conversationId,
        String toolName,
        String parametersJson,
        String resultSummaryJson,
        boolean success,
        String errorCode,
        long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Double estimatedCostUsd,
        String agentName,
        String initiator,
        Long inferenceDurationMs,
        Long toolExecutionDurationMs,
        Instant executedAt
) {
}
