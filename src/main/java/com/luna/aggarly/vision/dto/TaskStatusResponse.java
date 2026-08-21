package com.luna.aggarly.vision.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskStatusResponse(
        UUID taskId,
        UUID propertyId,
        UUID propertyImageId,
        String taskType,
        String status,
        String currentStage,
        int attemptCount,
        String lastErrorMessage,
        Instant scheduledAt,
        Instant finishedAt
) {}
