package com.luna.aggarly.common.admin.dto;

public record SystemTelemetryResponse(
    int hikariActiveConnections,
    int hikariIdleConnections,
    int hikariMaxConnections,
    double cacheHitRatioPercent,
    double clockSkewSeconds,
    long systemUptimeSeconds
) {}
