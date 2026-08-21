package com.luna.aggarly.cleaning.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AssignCleanerRequest(
        @NotNull(message = "Cleaner ID is required")
        UUID cleanerId,

        LocalDate scheduledDate,

        LocalTime scheduledStartTime
) {}
