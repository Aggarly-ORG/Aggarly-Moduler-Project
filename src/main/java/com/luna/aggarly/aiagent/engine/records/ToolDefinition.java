package com.luna.aggarly.aiagent.engine.records;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema,
        Map<String, Object> responseSchema
) {
    /** Backwards-compat constructor — no response schema */
    public ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {
        this(name, description, parametersSchema, Map.of());
    }
}
