package com.luna.aggarly.aiagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChatMessageRequest(
        UUID conversationId,

        @NotBlank(message = "Message content cannot be blank")
        String content,

        UUID chatConversationId,

        String screenshotUrl
) {
    public ChatMessageRequest(UUID conversationId, String content) {
        this(conversationId, content, null, null);
    }

    public ChatMessageRequest(UUID conversationId, String content, UUID chatConversationId) {
        this(conversationId, content, chatConversationId, null);
    }
}
