package com.luna.aggarly.cleaning.dto.request;

import com.luna.aggarly.cleaning.entity.enums.CleaningPriority;
import com.luna.aggarly.cleaning.entity.enums.CleaningTaskType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateCleaningTaskRequest(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        UUID bookingId,

        UUID assignedCleanerId,

        CleaningPriority priority,

        CleaningTaskType taskType,

        @NotNull(message = "Scheduled date is required")
        @FutureOrPresent(message = "Scheduled date must be today or in the future")
        LocalDate scheduledDate,

        LocalTime scheduledStartTime,

        Integer estimatedDurationMinutes,

        String cleanerNotes
) {}
