package com.luna.aggarly.vision.search.records;

import java.util.Map;
import java.util.UUID;

public record ChannelSearchResult(
        UUID imageId,
        UUID propertyId,
        float score,
        String channel,
        Map<String, Object> payload
) {}
