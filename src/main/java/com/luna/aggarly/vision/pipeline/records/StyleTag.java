package com.luna.aggarly.vision.pipeline.records;

import java.util.UUID;

public record StyleTag(
        String tag,
        double confidence,
        UUID sourceImageId,
        String evidenceType
) {
    public StyleTag(String tag, double confidence, UUID sourceImageId) {
        this(tag, confidence, sourceImageId, "VISION");
    }
}
