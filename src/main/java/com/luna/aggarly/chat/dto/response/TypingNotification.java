package com.luna.aggarly.chat.dto.response;

import java.util.UUID;

public record TypingNotification(
        UUID conversationId,
        UUID userId,
        boolean isTyping
) {}
