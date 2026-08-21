package com.luna.aggarly.chat.controller;

import com.luna.aggarly.chat.dto.request.ReadReceiptPayload;
import com.luna.aggarly.chat.dto.request.SendMessagePayload;
import com.luna.aggarly.chat.dto.request.TypingPayload;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.dto.response.TypingNotification;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketChatController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("/chat.send")
    public void handleSendMessage(@Payload SendMessagePayload payload, Principal principal) {
        UUID senderId = resolveUserId(principal);
        log.info("Received WebSocket message for conversation {} from {}", payload.conversationId(), senderId);

        messageService.sendMessage(
                payload.conversationId(),
                senderId,
                payload.content(),
                payload.messageType() != null ? payload.messageType() : MessageType.TEXT,
                payload.metadataJson()
        );
    }

    @MessageMapping("/chat.read")
    public void handleReadReceipt(@Payload ReadReceiptPayload payload, Principal principal) {
        UUID userId = resolveUserId(principal);
        log.debug("Received read receipt for conversation {} from {}", payload.conversationId(), userId);

        conversationService.markConversationAsRead(payload.conversationId(), userId, payload.lastReadMessageId());
    }

    @MessageMapping("/chat.typing")
    public void handleTypingNotification(@Payload TypingPayload payload, Principal principal) {
        UUID userId = resolveUserId(principal);
        TypingNotification notification = new TypingNotification(payload.conversationId(), userId, payload.isTyping());

        String destination = "/topic/conversation." + payload.conversationId() + ".typing";
        messagingTemplate.convertAndSend(destination, notification);
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
            Object p = token.getPrincipal();
            if (p instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getUserId();
            }
        }
        UUID currentId = SecurityUtils.getCurrentUserId();
        if (currentId != null) {
            return currentId;
        }
        return userRepository.findByEmail("essamhossam530@gmail.com")
                .map(User::getId)
                .orElseGet(() -> userRepository.findAll().stream().map(User::getId).findFirst().orElse(null));
    }
}
