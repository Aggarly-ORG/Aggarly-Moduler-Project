package com.luna.aggarly.host.dto;

import java.util.UUID;

public record DispatchTurnoverRequest(
        UUID sanctuaryId,
        String taskType,
        String notes,
        String priority
) {}