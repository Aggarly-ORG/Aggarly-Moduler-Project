package com.luna.aggarly.chat.dto.response;

import com.luna.aggarly.chat.entity.enums.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        MessageType messageType,
        String metadataJson,
        boolean edited,
        Instant createdAt
) {}
