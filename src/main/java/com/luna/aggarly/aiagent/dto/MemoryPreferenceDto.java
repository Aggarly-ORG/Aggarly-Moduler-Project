package com.luna.aggarly.aiagent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record MemoryPreferenceDto(
        @JsonAlias({"key", "label", "name"})
        String memoryKey,

        @JsonAlias({"value", "content", "preference", "text"})
        String memoryValue
) {
}
