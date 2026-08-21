package com.luna.aggarly.chat.dto.response;

import com.luna.aggarly.chat.entity.enums.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        UUID propertyId,
        UUID bookingId,
        UUID aiConversationId,
        String title,
        String name,
        Instant lastMessageAt,
        String lastMessagePreview,
        List<ConversationParticipantResponse> participants,
        List<MessageResponse> recentMessages,
        Instant createdAt
) {
    public ConversationResponse(
            UUID id,
            ConversationType type,
            UUID propertyId,
            UUID bookingId,
            UUID aiConversationId,
            String title,
            Instant lastMessageAt,
            String lastMessagePreview,
            List<ConversationParticipantResponse> participants,
            List<MessageResponse> recentMessages,
            Instant createdAt
    ) {
        this(id, type, propertyId, bookingId, aiConversationId, title, title, lastMessageAt, lastMessagePreview, participants, recentMessages, createdAt);
    }
}
