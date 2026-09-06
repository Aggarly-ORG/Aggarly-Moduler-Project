package com.luna.aggarly.aiagent.dto.activity;

import java.time.Instant;

public record AgentActivityBlock(
        String toolName,
        String friendlyTitle,
        String status,         // "COMPLETED", "FAILED"
        long durationMs,
        String inputSummary,
        String resultSummary,
        Instant timestamp
) {
}
