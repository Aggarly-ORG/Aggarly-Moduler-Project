package com.luna.aggarly.property.dto.response;

import java.util.Map;

public record LiveGuestResidentsResponse(
    long activeResidentsCount,
    Map<String, Double> hubDistribution,
    long disputedCount
) {}
