package com.luna.aggarly.aiagent.engine.records;

import java.util.List;

public record LlmToolCallResponse(
        String textResponse,
        List<LlmToolCall> toolCalls
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
