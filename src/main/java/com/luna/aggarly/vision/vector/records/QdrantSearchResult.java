package com.luna.aggarly.vision.vector.records;

import java.util.Map;
import java.util.UUID;

public record QdrantSearchResult(
        UUID id,
        float score,
        Map<String, Object> payload
) {}
