package com.luna.aggarly.chat.dto.request;

import com.luna.aggarly.chat.entity.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendMessagePayload(
        @NotNull(message = "Conversation ID is required")
        UUID conversationId,

        @NotBlank(message = "Message content is required")
        String content,

        MessageType messageType,

        String metadataJson
) {}
