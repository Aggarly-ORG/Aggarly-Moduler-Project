package com.luna.aggarly.chat.dto.request;

import com.luna.aggarly.chat.entity.enums.ConversationType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull(message = "Conversation type is required")
        ConversationType type,

        UUID recipientId,

        UUID propertyId,

        UUID bookingId,

        String title,

        String initialMessage
) {}
