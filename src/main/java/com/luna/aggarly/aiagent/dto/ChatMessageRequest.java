package com.luna.aggarly.aiagent.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChatMessageRequest(
        UUID conversationId,

        @NotBlank(message = "Message content cannot be blank")
        String content,

        UUID chatConversationId
) {
    public ChatMessageRequest(UUID conversationId, String content) {
        this(conversationId, content, null);
    }
}
