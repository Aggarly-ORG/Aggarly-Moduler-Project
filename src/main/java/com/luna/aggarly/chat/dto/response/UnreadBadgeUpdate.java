package com.luna.aggarly.chat.dto.response;

import java.util.UUID;

public record UnreadBadgeUpdate(
        UUID conversationId,
        int conversationUnreadCount,
        int totalUnreadCount
) {}
