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
        Instant executedAt
) {
}
