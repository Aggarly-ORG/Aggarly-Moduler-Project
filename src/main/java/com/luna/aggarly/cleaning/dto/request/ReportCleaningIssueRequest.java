package com.luna.aggarly.cleaning.dto.request;

import com.luna.aggarly.cleaning.entity.enums.IssueSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCleaningIssueRequest(
        @NotBlank(message = "Issue title is required")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @NotBlank(message = "Issue description is required")
        String description,

        @NotNull(message = "Severity is required")
        IssueSeverity severity,

        String photoKeys
) {}
