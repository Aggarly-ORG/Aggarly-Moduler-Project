package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;

public record PropertyCompareResponse(
        int totalCompared,
        List<PropertyComparisonItem> properties
) {}
