package com.luna.aggarly.chat.service;

import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.Message;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.entity.enums.ParticipantRole;
import com.luna.aggarly.chat.mapper.ChatMapper;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.repository.MessageReadReceiptRepository;
import com.luna.aggarly.chat.repository.MessageRepository;
import com.luna.aggarly.chat.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private MessageReadReceiptRepository readReceiptRepository;

    @Mock
    private RedisChatPublisher redisChatPublisher;

    @Mock
    private ChatAiBridgeService chatAiBridgeService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

    private UUID user1Id;
    private UUID user2Id;
    private UUID conversationId;
    private UUID messageId;
    private Conversation sampleConversation;
    private ConversationParticipant participant2;

    @BeforeEach
    void setUp() {
        messageService.setConversationService(conversationService);
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();

        sampleConversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .participants(new ArrayList<>())
                .build();
        sampleConversation.setId(conversationId);

        ConversationParticipant p1 = ConversationParticipant.builder()
                .conversation(sampleConversation)
                .userId(user1Id)
                .role(ParticipantRole.GUEST)
                .unreadCount(0)
                .build();

        participant2 = ConversationParticipant.builder()
                .conversation(sampleConversation)
                .userId(user2Id)
                .role(ParticipantRole.HOST)
                .unreadCount(0)
                .build();

        sampleConversation.getParticipants().add(p1);
        sampleConversation.getParticipants().add(participant2);

        lenient().when(participantRepository.existsByConversationIdAndUserId(any(), any())).thenReturn(true);
        lenient().when(participantRepository.findByConversationId(any())).thenReturn(List.of(p1, participant2));
    }

    @Test
    @DisplayName("sendMessage should persist message, increment unread for recipient, and publish to Redis")
    void testSendMessage() {
        Message sampleMessage = Message.builder()
                .conversation(sampleConversation)
                .senderId(user1Id)
                .content("Hello, is the place available?")
                .messageType(MessageType.TEXT)
                .build();
        sampleMessage.setId(messageId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(sampleConversation));
        when(messageRepository.save(any(Message.class))).thenReturn(sampleMessage);
        when(chatMapper.toResponse(sampleMessage)).thenReturn(new MessageResponse(
                messageId, conversationId, user1Id, "Hello, is the place available?", MessageType.TEXT, null, false, Instant.now()
        ));

        MessageResponse response = messageService.sendMessage(conversationId, user1Id, "Hello, is the place available?", MessageType.TEXT, null);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(messageId);
        assertThat(participant2.getUnreadCount()).isEqualTo(1);
        verify(redisChatPublisher).publishMessage(eq(conversationId), any(MessageResponse.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("sendMessage with zeroes UUID should auto-resolve and create conversation")
    void testSendMessageWithZeroesUuid() {
        UUID zeroesUuid = new UUID(0L, 0L);
        when(conversationService.getOrCreateAiConciergeConversation(user1Id)).thenReturn(new ConversationResponse(
                conversationId, ConversationType.AI_CONCIERGE, null, null, null, "AI Concierge", Instant.now(), null, List.of(), List.of(), Instant.now()
        ));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(sampleConversation));

        Message sampleMessage = Message.builder()
                .conversation(sampleConversation)
                .senderId(user1Id)
                .content("Find apartments in Paris")
                .messageType(MessageType.TEXT)
                .build();
        sampleMessage.setId(messageId);

        when(messageRepository.save(any(Message.class))).thenReturn(sampleMessage);
        when(chatMapper.toResponse(sampleMessage)).thenReturn(new MessageResponse(
                messageId, conversationId, user1Id, "Find apartments in Paris", MessageType.TEXT, null, false, Instant.now()
        ));

        MessageResponse response = messageService.sendMessage(zeroesUuid, user1Id, "Find apartments in Paris", MessageType.TEXT, null);

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isEqualTo(conversationId);
        verify(conversationService).getOrCreateAiConciergeConversation(user1Id);
    }

    @Test
    @DisplayName("sendMessage in AI_CONCIERGE conversation should trigger ChatAiBridgeService async")
    void testSendMessageInAiConcierge() {
        sampleConversation.setType(ConversationType.AI_CONCIERGE);

        Message sampleMessage = Message.builder()
                .conversation(sampleConversation)
                .senderId(user1Id)
                .content("Find me apartments in Rome")
                .messageType(MessageType.TEXT)
                .build();
        sampleMessage.setId(messageId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(sampleConversation));
        when(messageRepository.save(any(Message.class))).thenReturn(sampleMessage);
        when(chatMapper.toResponse(sampleMessage)).thenReturn(new MessageResponse(
                messageId, conversationId, user1Id, "Find me apartments in Rome", MessageType.TEXT, null, false, Instant.now()
        ));

        messageService.sendMessage(conversationId, user1Id, "Find me apartments in Rome", MessageType.TEXT, null);

        verify(chatAiBridgeService).processAiChatTurnAsync(conversationId, user1Id, "Find me apartments in Rome");
    }
}
