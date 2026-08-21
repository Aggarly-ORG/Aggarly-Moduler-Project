package com.luna.aggarly.chat.service;

import java.util.UUID;

public interface ChatAiBridgeService {

    UUID AI_BOT_SYSTEM_ID = UUID.fromString("aaac7011-3626-460c-a47e-c94535d34c65");

    void processAiChatTurnAsync(UUID conversationId, UUID userId, String userMessage);
}
