package com.luna.aggarly.vision.pipeline.records;

import java.util.UUID;

public record PreprocessedImage(
        UUID imageId,
        UUID propertyId,
        byte[] bytes,
        int widthPx,
        int heightPx,
        String format,
        String objectKey
) {}
