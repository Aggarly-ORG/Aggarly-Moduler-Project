package com.luna.aggarly.aiagent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.engine.ConversationManager;
import com.luna.aggarly.aiagent.service.AiChatService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final ConversationManager conversationManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ChatMessageResponse processAiChatMessage(UUID conversationId, String content, UserPrincipal user) {
        log.info("Processing chat message for AI Contact from userId={}, conversationId={}",
                user != null ? user.getUserId() : "anonymous", conversationId);
        ChatMessageRequest request = new ChatMessageRequest(conversationId, content);
        return conversationManager.handleMessage(request, user);
    }
}
