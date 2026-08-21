package com.luna.aggarly.chat.dto.response;

import com.luna.aggarly.chat.entity.enums.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID id,
        ConversationType type,
        UUID propertyId,
        UUID bookingId,
        String title,
        String name,
        Instant lastMessageAt,
        String lastMessagePreview,
        int unreadCount,
        List<ConversationParticipantResponse> participants
) {
    public ConversationSummaryResponse(
            UUID id,
            ConversationType type,
            UUID propertyId,
            UUID bookingId,
            String title,
            Instant lastMessageAt,
            String lastMessagePreview,
            int unreadCount,
            List<ConversationParticipantResponse> participants
    ) {
        this(id, type, propertyId, bookingId, title, title, lastMessageAt, lastMessagePreview, unreadCount, participants);
    }
}
