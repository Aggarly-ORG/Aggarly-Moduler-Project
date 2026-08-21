package com.luna.aggarly.cleaning.dto.response;

import com.luna.aggarly.cleaning.entity.enums.CleaningPhotoType;

import java.time.Instant;
import java.util.UUID;

public record CleaningPhotoResponse(
        UUID id,
        UUID cleaningTaskId,
        String roomName,
        CleaningPhotoType photoType,
        String objectKey,
        UUID uploadedBy,
        Instant createdAt
) {}
