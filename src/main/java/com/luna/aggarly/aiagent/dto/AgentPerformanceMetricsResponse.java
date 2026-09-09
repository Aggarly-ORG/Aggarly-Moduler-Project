package com.luna.aggarly.aiagent.dto;

public record AgentPerformanceMetricsResponse(
    String agentName,
    String model,
    long totalInvocations,
    double successRate,
    double avgDurationMs,
    long totalTokens,
    double loadPercentile
) {}
