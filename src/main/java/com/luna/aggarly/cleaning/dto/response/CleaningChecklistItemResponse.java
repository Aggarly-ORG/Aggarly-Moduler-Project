package com.luna.aggarly.cleaning.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CleaningChecklistItemResponse(
        UUID id,
        UUID checklistId,
        String taskDescription,
        boolean completed,
        Instant completedAt,
        String notes
) {}
