package com.luna.aggarly.vision.event;

import java.util.UUID;

public record VisionTaskDeadLetterEvent(
        UUID taskId,
        UUID propertyId,
        UUID propertyImageId,
        String taskType,
        String failureReason
) {}
