package com.luna.aggarly.common.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record ActionLedgerItemResponse(
    UUID id,
    String type,
    String severity,
    String title,
    String description,
    String referenceId,
    String status,
    Instant createdAt
) {}
