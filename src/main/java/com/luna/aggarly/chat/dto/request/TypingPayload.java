package com.luna.aggarly.chat.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TypingPayload(
        @NotNull(message = "Conversation ID is required")
        UUID conversationId,

        @NotNull
        Boolean isTyping
) {}
