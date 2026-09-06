package com.luna.aggarly.chat.dto.response;

import com.luna.aggarly.chat.entity.enums.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantResponse(
        UUID id,
        UUID userId,
        /** Resolved from User.displayName → firstName + lastName → username */
        String displayName,
        String username,
        String avatarUrl,
        ParticipantRole role,
        Instant lastReadAt,
        int unreadCount,
        boolean muted,
        boolean archived,
        Instant joinedAt
) {}
