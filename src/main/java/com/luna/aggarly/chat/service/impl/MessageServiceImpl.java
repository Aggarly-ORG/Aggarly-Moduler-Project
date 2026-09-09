package com.luna.aggarly.chat.service.impl;

import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.Message;
import com.luna.aggarly.chat.entity.MessageReadReceipt;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.event.MessageSentEvent;
import com.luna.aggarly.chat.exceptions.ConversationNotFoundException;
import com.luna.aggarly.chat.exceptions.UnauthorizedChatAccessException;
import com.luna.aggarly.chat.mapper.ChatMapper;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.repository.MessageReadReceiptRepository;
import com.luna.aggarly.chat.repository.MessageRepository;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.chat.service.RedisChatPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    public static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageReadReceiptRepository readReceiptRepository;
    private final RedisChatPublisher redisChatPublisher;
    private final ChatAiBridgeService chatAiBridgeService;
    private final ChatMapper chatMapper;
    private final ApplicationEventPublisher eventPublisher;
    private ConversationService conversationService;

    public MessageServiceImpl(MessageRepository messageRepository,
                              ConversationRepository conversationRepository,
                              ConversationParticipantRepository participantRepository,
                              MessageReadReceiptRepository readReceiptRepository,
                              RedisChatPublisher redisChatPublisher,
                              ChatAiBridgeService chatAiBridgeService,
                              ChatMapper chatMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.readReceiptRepository = readReceiptRepository;
        this.redisChatPublisher = redisChatPublisher;
        this.chatAiBridgeService = chatAiBridgeService;
        this.chatMapper = chatMapper;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setConversationService(@Lazy ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public static boolean isZeroOrNull(UUID id) {
        return id == null || id.equals(ZERO_UUID);
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, String content, MessageType messageType, String metadataJson) {
        Conversation conversation;

        final UUID inputConvId = conversationId;
        // Auto-create or resolve conversation if zeroes UUID or null or not found
        if (isZeroOrNull(inputConvId)) {
            ConversationResponse convResp = conversationService.getOrCreateAiConciergeConversation(senderId);
            conversation = conversationRepository.findById(convResp.id())
                    .orElseThrow(() -> new ConversationNotFoundException(convResp.id()));
            log.info("Zeroes UUID passed; automatically resolved to conversation {}", conversation.getId());
        } else {
            conversation = conversationRepository.findById(inputConvId)
                    .orElseGet(() -> {
                        log.info("Conversation {} not found, auto-creating AI Concierge conversation for sender {}", inputConvId, senderId);
                        ConversationResponse convResp = conversationService.getOrCreateAiConciergeConversation(senderId);
                        return conversationRepository.findById(convResp.id())
                                .orElseThrow(() -> new ConversationNotFoundException(convResp.id()));
                    });
        }

        final UUID finalConversationId = conversation.getId();
        boolean isParticipant = participantRepository.existsByConversationIdAndUserId(finalConversationId, senderId);

        if (!isParticipant && !senderId.equals(ChatAiBridgeService.AI_BOT_SYSTEM_ID)) {
            // If sender is not yet a participant (e.g. new user), add them as participant
            ConversationParticipant newParticipant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .userId(senderId)
                    .role(com.luna.aggarly.chat.entity.enums.ParticipantRole.GUEST)
                    .unreadCount(0)
                    .lastReadAt(Instant.now())
                    .build();
            participantRepository.save(newParticipant);
        }

        Message message = Message.builder()
                .conversation(conversation)
                .senderId(senderId)
                .content(content)
                .messageType(messageType != null ? messageType : MessageType.TEXT)
                .metadataJson(metadataJson)
                .build();

        Message saved = messageRepository.save(message);

        // Update conversation summary
        conversation.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now());
        String snippet = content.length() > 60 ? content.substring(0, 57) + "..." : content;
        conversation.setLastMessagePreview(snippet);
        conversationRepository.save(conversation);

        // Increment unread count for recipients
        List<ConversationParticipant> participants = participantRepository.findByConversationId(finalConversationId);
        List<UUID> recipientIds = new ArrayList<>();
        for (ConversationParticipant p : participants) {
            if (!p.getUserId().equals(senderId)) {
                p.setUnreadCount(p.getUnreadCount() + 1);
                participantRepository.save(p);
                recipientIds.add(p.getUserId());
            }
        }

        MessageResponse response = chatMapper.toResponse(saved);

        // Broadcast over Redis pub/sub (which dispatches to STOMP WebSockets)
        redisChatPublisher.publishMessage(finalConversationId, response);

        // Publish domain event for push/email notification triggers
        eventPublisher.publishEvent(new MessageSentEvent(
                this,
                saved.getId(),
                finalConversationId,
                senderId,
                recipientIds,
                saved.getContent(),
                saved.getMessageType()
        ));

        // Trigger AI response async if:
        // 1. Conversation type is AI_CONCIERGE, OR
        // 2. Message mentions @Lumen or @AI in any conversation (e.g. DIRECT or BOOKING_INQUIRY)
        boolean isAiMentioned = content != null && (
                content.toLowerCase().contains("@lumen") ||
                content.toLowerCase().contains("@ai")
        );
        boolean isAiConversation = conversation.getType() == ConversationType.AI_CONCIERGE ||
                                   conversation.getType() == ConversationType.PROPERTY_CONVERSATION;

        boolean isImageOrSkip = messageType == MessageType.IMAGE || (metadataJson != null && metadataJson.contains("\"skipAiTurn\":true"));

        if ((isAiConversation || isAiMentioned) && !senderId.equals(ChatAiBridgeService.AI_BOT_SYSTEM_ID) && !isImageOrSkip) {
            // If AI is mentioned in a direct conversation, ensure AI_BOT is a participant
            if (!participantRepository.existsByConversationIdAndUserId(finalConversationId, ChatAiBridgeService.AI_BOT_SYSTEM_ID)) {
                ConversationParticipant aiParticipant = ConversationParticipant.builder()
                        .conversation(conversation)
                        .userId(ChatAiBridgeService.AI_BOT_SYSTEM_ID)
                        .role(com.luna.aggarly.chat.entity.enums.ParticipantRole.AI_BOT)
                        .unreadCount(0)
                        .lastReadAt(Instant.now())
                        .build();
                participantRepository.save(aiParticipant);
                log.info("Added AI_BOT Lumen participant to conversation {}", finalConversationId);
            }

            chatAiBridgeService.processAiChatTurnAsync(finalConversationId, senderId, content, metadataJson);
        }

        return response;
    }

    @Override
    @Transactional
    public MessageResponse sendSystemMessage(UUID conversationId, String content, String metadataJson) {
        return sendMessage(conversationId, ChatAiBridgeService.AI_BOT_SYSTEM_ID, content, MessageType.SYSTEM, metadataJson);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID conversationId, UUID currentUserId, Pageable pageable) {
        if (isZeroOrNull(conversationId)) {
            ConversationResponse convResp = conversationService.getOrCreateAiConciergeConversation(currentUserId);
            conversationId = convResp.id();
        }

        final UUID finalConvId = conversationId;
        Conversation conversation = conversationRepository.findById(finalConvId)
                .orElseThrow(() -> new ConversationNotFoundException(finalConvId));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));

        if (!isParticipant) {
            throw new UnauthorizedChatAccessException("You are not authorized to view messages in this conversation");
        }

        return messageRepository.findByConversationIdOrderByCreatedAtDesc(finalConvId, pageable)
                .map(chatMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBefore(UUID conversationId, Instant before, int limit, UUID currentUserId) {
        if (isZeroOrNull(conversationId)) {
            ConversationResponse convResp = conversationService.getOrCreateAiConciergeConversation(currentUserId);
            conversationId = convResp.id();
        }

        final UUID finalConvId = conversationId;
        Conversation conversation = conversationRepository.findById(finalConvId)
                .orElseThrow(() -> new ConversationNotFoundException(finalConvId));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));

        if (!isParticipant) {
            throw new UnauthorizedChatAccessException("You are not authorized to view messages in this conversation");
        }

        int max = Math.min(Math.max(1, limit), 100);
        return chatMapper.toMessageResponseList(
                messageRepository.findMessagesBefore(finalConvId, before, PageRequest.of(0, max)));
    }

    @Autowired(required = false)
    private com.luna.aggarly.aiagent.repository.AiMessageRepository aiMessageRepository;

    @Override
    @Transactional
    public void recordReadReceipt(UUID messageId, UUID userId) {
        if (readReceiptRepository.findByMessageIdAndUserId(messageId, userId).isEmpty()) {
            messageRepository.findById(messageId).ifPresent(message -> {
                MessageReadReceipt receipt = MessageReadReceipt.builder()
                        .message(message)
                        .userId(userId)
                        .readAt(Instant.now())
                        .build();
                readReceiptRepository.save(receipt);
            });
        }
    }

    @Override
    @Transactional
    public void clearConversationMessages(UUID conversationId, UUID currentUserId) {
        if (isZeroOrNull(conversationId)) {
            ConversationResponse convResp = conversationService.getOrCreateAiConciergeConversation(currentUserId);
            conversationId = convResp.id();
        }

        final UUID finalConvId = conversationId;
        Conversation conversation = conversationRepository.findById(finalConvId)
                .orElseThrow(() -> new ConversationNotFoundException(finalConvId));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(currentUserId));

        if (!isParticipant) {
            throw new UnauthorizedChatAccessException("You are not authorized to clear messages in this conversation");
        }

        // Clean up only AI agent conversation context messages (keep human chat history intact)
        if (aiMessageRepository != null) {
            if (conversation.getAiConversationId() != null) {
                var aiMessages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getAiConversationId());
                if (!aiMessages.isEmpty()) {
                    aiMessageRepository.deleteAll(aiMessages);
                }
            }
            var directAiMessages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(finalConvId);
            if (!directAiMessages.isEmpty()) {
                aiMessageRepository.deleteAll(directAiMessages);
            }
        }
    }
}
