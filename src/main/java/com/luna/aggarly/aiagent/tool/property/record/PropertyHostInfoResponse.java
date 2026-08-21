package com.luna.aggarly.aiagent.tool.property.record;

import java.util.UUID;

public record PropertyHostInfoResponse(
        UUID hostId,
        String hostName,
        String email,
        boolean isSuperhost,
        String responseRate,
        String responseTime,
        long totalListings
) {}
