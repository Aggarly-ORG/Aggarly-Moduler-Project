package com.luna.aggarly.aiagent.dto;

public record AiUsageStatsResponse(
        long totalInvocations,
        long successfulInvocations,
        long failedInvocations,
        long totalPromptTokens,
        long totalCompletionTokens,
        double totalEstimatedCostUsd,
        double averageDurationMs
) {
}
