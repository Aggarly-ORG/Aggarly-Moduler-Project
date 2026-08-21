package com.luna.aggarly.vision.pipeline.records;

import java.util.UUID;

public record DetectedAmenity(
        String amenityName,
        double confidence,
        UUID sourceImageId,
        String evidenceType
) {
    public DetectedAmenity(String amenityName, double confidence, UUID sourceImageId) {
        this(amenityName, confidence, sourceImageId, "VISION");
    }
}
