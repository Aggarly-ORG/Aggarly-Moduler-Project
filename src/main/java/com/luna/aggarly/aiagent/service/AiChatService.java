package com.luna.aggarly.aiagent.service;

import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.user.security.UserPrincipal;

import java.util.UUID;

public interface AiChatService {
    ChatMessageResponse processAiChatMessage(UUID conversationId, String content, UserPrincipal user);
}
