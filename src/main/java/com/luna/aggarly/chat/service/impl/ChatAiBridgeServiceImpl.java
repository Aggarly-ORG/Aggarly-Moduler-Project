package com.luna.aggarly.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.engine.ConversationManager;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ChatAiBridgeServiceImpl implements ChatAiBridgeService {

    private final ConversationManager aiConversationManager;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private MessageService messageService;

    public ChatAiBridgeServiceImpl(@Lazy ConversationManager aiConversationManager,
                                   ConversationRepository conversationRepository,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.aiConversationManager = aiConversationManager;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setMessageService(@Lazy MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    @Async
    public void processAiChatTurnAsync(UUID conversationId, UUID userId, String userMessage) {
        log.info("Processing asynchronous AI chat turn for conversation {}, user {}", conversationId, userId);
        try {
            Optional<User> userOpt = Optional.empty();
            if (userId != null) {
                userOpt = userRepository.findById(userId);
            }
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail("essamhossam530@gmail.com")
                        .or(() -> userRepository.findAll().stream().findFirst());
            }

            UserPrincipal principal = userOpt.map(UserPrincipal::new).orElse(null);

            // Establish SecurityContextHolder for this @Async thread
            if (principal != null) {
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            }

            // Fetch the linked AI conversation ID if already established
            UUID aiConversationId = null;
            Optional<Conversation> chatConvOpt = conversationRepository.findById(conversationId);
            if (chatConvOpt.isPresent()) {
                aiConversationId = chatConvOpt.get().getAiConversationId();
            }

            ChatMessageRequest aiRequest = new ChatMessageRequest(aiConversationId, userMessage, conversationId);
            ChatMessageResponse aiResponse = aiConversationManager.handleMessage(aiRequest, principal);

            // Link newly created AI conversation ID to this chat conversation
            if (chatConvOpt.isPresent() && (aiConversationId == null || !aiConversationId.equals(aiResponse.conversationId()))) {
                Conversation chatConv = chatConvOpt.get();
                chatConv.setAiConversationId(aiResponse.conversationId());
                conversationRepository.save(chatConv);
                log.info("Linked chat conversation {} to AI session {}", conversationId, aiResponse.conversationId());
            }

            MessageType type = MessageType.TEXT;
            String metadataJson = aiResponse.metadataJson();

            if (aiResponse.requiresConfirmation()) {
                type = MessageType.ACTION_CARD;
                Map<String, Object> cardMap = new HashMap<>();
                cardMap.put("cardType", "AI_CONFIRMATION_REQUIRED");
                cardMap.put("confirmationToken", aiResponse.confirmationToken());
                cardMap.put("pendingToolName", aiResponse.pendingToolName());
                cardMap.put("toolCalls", aiResponse.toolCalls());
                metadataJson = objectMapper.writeValueAsString(cardMap);
            }

            messageService.sendMessage(conversationId, AI_BOT_SYSTEM_ID, aiResponse.content(), type, metadataJson);
            log.info("Delivered AI response to conversation {}", conversationId);

        } catch (Exception e) {
            log.error("Error generating AI response in chat bridge", e);
            String errorJson = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.formatResponse(
                    null,
                    java.util.List.of(com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.error("ERROR", "I encountered an issue processing your request. Please try again in a moment."))
            );
            messageService.sendMessage(conversationId, AI_BOT_SYSTEM_ID, errorJson, MessageType.SYSTEM, null);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
