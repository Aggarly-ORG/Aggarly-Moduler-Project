package com.luna.aggarly.cleaning.dto.request;

import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "Target status is required")
        CleaningStatus status,

        String cleanerNotes
) {}
