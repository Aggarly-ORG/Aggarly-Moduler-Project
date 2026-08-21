package com.luna.aggarly.vision.pipeline.records;

import java.util.UUID;

public record DetectedObject(
        String objectName,
        double confidence,
        UUID sourceImageId,
        String evidenceType
) {
    public DetectedObject(String objectName, double confidence, UUID sourceImageId) {
        this(objectName, confidence, sourceImageId, "VISION");
    }
}
