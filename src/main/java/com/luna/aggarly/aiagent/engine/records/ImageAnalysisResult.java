package com.luna.aggarly.aiagent.engine.records;

import java.util.List;

public record ImageAnalysisResult(
        String aiCaption,
        String altText,
        String ocrText,
        List<String> detectedObjects,
        List<String> styleTags,
        String moderationStatus,
        String moderationReason
) {
}
