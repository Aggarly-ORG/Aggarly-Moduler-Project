package com.luna.aggarly.aiagent.tool.schedule.record;

import java.util.UUID;

public record TaskActionResponse(
        UUID taskId,
        String status,
        String message
) {}
