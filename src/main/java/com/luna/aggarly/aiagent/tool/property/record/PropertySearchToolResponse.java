package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;

public record PropertySearchToolResponse(
        long totalProperties,
        int page,
        int totalPages,
        List<PropertySummaryItem> properties
) {}
