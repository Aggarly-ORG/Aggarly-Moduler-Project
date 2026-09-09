package com.luna.aggarly.aiagent.engine.records;

public record ChatMessage(
        String role,
        String content,
        String toolName,
        String imageUrl
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage user(String content, String imageUrl) {
        return new ChatMessage("user", content, null, imageUrl);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage toolResponse(String toolName, String content) {
        return new ChatMessage("tool", content, toolName, null);
    }
}
