package com.luna.aggarly.aiagent.tool.schedule.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ListSchedulesParams(
        @JsonPropertyDescription("0-indexed page number (default 0).")
        Integer page,

        @JsonPropertyDescription("Number of records per page (default 20, max 100).")
        Integer size
) {}
