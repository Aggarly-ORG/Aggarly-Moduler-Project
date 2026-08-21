package com.luna.aggarly.vision.vector.records;

import java.util.Map;
import java.util.UUID;

public record QdrantMultiVectorPoint(
        UUID id,
        Map<String, float[]> vectors,
        Map<String, Object> payload
) {}
