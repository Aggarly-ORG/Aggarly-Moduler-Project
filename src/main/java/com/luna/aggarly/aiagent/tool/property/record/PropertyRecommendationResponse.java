package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;

public record PropertyRecommendationResponse(
        int totalRecommendations,
        String reason,
        List<PropertySummaryItem> recommendations
) {}
