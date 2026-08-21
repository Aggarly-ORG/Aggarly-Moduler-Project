package com.luna.aggarly.chat.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReadReceiptPayload(
        @NotNull(message = "Conversation ID is required")
        UUID conversationId,

        UUID lastReadMessageId
) {}
