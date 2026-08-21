package com.luna.aggarly.chat.dto.response;

import com.luna.aggarly.chat.entity.enums.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantResponse(
        UUID id,
        UUID userId,
        ParticipantRole role,
        Instant lastReadAt,
        int unreadCount,
        boolean muted,
        boolean archived,
        Instant joinedAt
) {}
