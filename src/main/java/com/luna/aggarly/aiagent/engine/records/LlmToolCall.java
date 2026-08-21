package com.luna.aggarly.aiagent.engine.records;

import java.util.Map;

public record LlmToolCall(
        String name,
        Map<String, Object> arguments
) {
}
