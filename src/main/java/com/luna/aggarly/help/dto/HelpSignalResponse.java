package com.luna.aggarly.help.dto;

import java.time.Instant;
import java.util.UUID;

public record HelpSignalResponse(
        UUID id,
        String residencyRef,
        String situationType,
        String description,
        String phone,
        String status,
        Instant createdAt,
        Instant resolvedAt
) {}