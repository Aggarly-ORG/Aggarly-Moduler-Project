package com.luna.aggarly.cleaning.dto.request;

import jakarta.validation.constraints.NotNull;

public record SubmitChecklistItemRequest(
        @NotNull(message = "Completion status is required")
        Boolean completed,

        String notes
) {}
