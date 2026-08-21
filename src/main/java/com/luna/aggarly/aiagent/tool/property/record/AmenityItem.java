package com.luna.aggarly.aiagent.tool.property.record;

import java.util.UUID;

public record AmenityItem(
        UUID id,
        String name,
        String category,
        String icon
) {}
