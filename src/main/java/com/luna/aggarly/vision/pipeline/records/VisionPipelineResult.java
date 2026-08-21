package com.luna.aggarly.vision.pipeline.records;

import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;

public record VisionPipelineResult(
        PropertyImageAiMetadata metadata,
        boolean requiresEmbedding,
        String summaryMessage
) {}
