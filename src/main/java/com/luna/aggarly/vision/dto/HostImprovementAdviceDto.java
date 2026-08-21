package com.luna.aggarly.vision.dto;

import java.util.List;
import java.util.UUID;

public record HostImprovementAdviceDto(
        UUID propertyId,
        String summaryAdvice,
        List<String> suggestedAnglesToCapture,
        List<String> lowQualityImagesToRetake,
        UUID recommendedCoverImageId
) {}
