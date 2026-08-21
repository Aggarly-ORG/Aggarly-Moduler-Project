package com.luna.aggarly.aiagent.tool.schedule.record;

import com.luna.aggarly.scheduler.dto.ScheduledTaskSummaryResponse;

import java.util.List;

public record ListSchedulesResponse(
        List<ScheduledTaskSummaryResponse> tasks,
        long totalCount
) {}
