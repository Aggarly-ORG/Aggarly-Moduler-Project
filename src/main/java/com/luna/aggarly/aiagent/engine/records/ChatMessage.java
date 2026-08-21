package com.luna.aggarly.aiagent.engine.records;

public record ChatMessage(
        String role,
        String content,
        String toolName
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null);
    }

    public static ChatMessage toolResponse(String toolName, String content) {
        return new ChatMessage("tool", content, toolName);
    }
}
