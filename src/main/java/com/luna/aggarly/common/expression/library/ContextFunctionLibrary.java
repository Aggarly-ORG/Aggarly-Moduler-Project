package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@ExpressionFunctionLibrary(value = "context", prefix = "ContextUtils")
public class ContextFunctionLibrary {

    private final ConversationRepository conversationRepository;

    public ContextFunctionLibrary(@Autowired(required = false) @Lazy ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @ExpressionFunction(value = "current_conversation", description = "Returns active chat conversation ID (conversations table ID, not AI conversation ID)")
    public String currentConversation(ExpressionContext context) {
        if (context == null) return null;

        String rawId = null;
        if (context.variables() != null) {
            if (context.variables().containsKey("chatConversationId")) {
                rawId = String.valueOf(context.variables().get("chatConversationId"));
            } else if (context.variables().containsKey("conversationId")) {
                rawId = String.valueOf(context.variables().get("conversationId"));
            }
        }
        if (rawId == null && context.rootObject() != null) {
            if (context.rootObject().containsKey("chatConversationId")) {
                rawId = String.valueOf(context.rootObject().get("chatConversationId"));
            } else if (context.rootObject().containsKey("conversationId")) {
                rawId = String.valueOf(context.rootObject().get("conversationId"));
            }
        }

        // If no ID in context, try resolving active AI concierge conversation for the user
        if (rawId == null || rawId.isBlank()) {
            if (conversationRepository != null) {
                UUID userId = context.userId();
                if (userId == null) {
                    userId = SecurityUtils.getCurrentUserId();
                }
                if (userId != null) {
                    Optional<Conversation> aiConv = conversationRepository.findAiConciergeConversation(userId);
                    if (aiConv.isPresent()) {
                        return aiConv.get().getId().toString();
                    }
                }
            }
            return null;
        }

        // If an ID is found and conversationRepository is available, ensure we return the chat conversation ID
        if (conversationRepository != null) {
            try {
                UUID parsedUuid = UUID.fromString(rawId);
                // Check if rawId is already the chat conversation ID
                Optional<Conversation> direct = conversationRepository.findById(parsedUuid);
                if (direct.isPresent()) {
                    return direct.get().getId().toString();
                }
                // Check if rawId is an AI conversation ID and map to the real chat conversation
                Optional<Conversation> mapped = conversationRepository.findByAiConversationId(parsedUuid);
                if (mapped.isPresent()) {
                    return mapped.get().getId().toString();
                }
            } catch (Exception ex) {
                log.debug("Could not resolve chat conversation ID for '{}': {}", rawId, ex.getMessage());
            }
        }

        return rawId;
    }

    @ExpressionFunction(value = "current_user_id", description = "Returns active user ID from context or security")
    public String currentUserId(ExpressionContext context) {
        if (context != null && context.userId() != null) {
            return context.userId().toString();
        }
        UUID secUser = SecurityUtils.getCurrentUserId();
        return secUser != null ? secUser.toString() : null;
    }

    @ExpressionFunction(value = "origin_channel", description = "Originating client channel (WEB, MOBILE, AI_CONCIERGE)")
    public String originChannel(ExpressionContext context) {
        if (context != null && context.variables() != null) {
            if (context.variables().containsKey("originChannel")) {
                return String.valueOf(context.variables().get("originChannel"));
            }
            if (context.variables().containsKey("channel")) {
                return String.valueOf(context.variables().get("channel"));
            }
        }
        return "AI_CONCIERGE";
    }

    @ExpressionFunction(value = "timezone", description = "Active execution timezone")
    public String timezone(ExpressionContext context) {
        return (context != null && context.timezone() != null) ? context.timezone() : "UTC";
    }
}
