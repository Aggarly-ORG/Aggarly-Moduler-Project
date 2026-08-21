package com.luna.aggarly.chat.service;

import com.luna.aggarly.chat.dto.request.CreateConversationRequest;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.ParticipantRole;
import com.luna.aggarly.chat.exceptions.UnauthorizedChatAccessException;
import com.luna.aggarly.chat.mapper.ChatMapper;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.repository.MessageRepository;
import com.luna.aggarly.chat.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatMapper chatMapper;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private UUID user1Id;
    private UUID user2Id;
    private UUID conversationId;
    private Conversation sampleConversation;

    @BeforeEach
    void setUp() {
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        sampleConversation = Conversation.builder()
                .type(ConversationType.DIRECT)
                .title("Direct Chat")
                .lastMessageAt(Instant.now())
                .participants(new ArrayList<>())
                .build();
        sampleConversation.setId(conversationId);

        ConversationParticipant p1 = ConversationParticipant.builder()
                .conversation(sampleConversation)
                .userId(user1Id)
                .role(ParticipantRole.GUEST)
                .build();

        ConversationParticipant p2 = ConversationParticipant.builder()
                .conversation(sampleConversation)
                .userId(user2Id)
                .role(ParticipantRole.HOST)
                .build();

        sampleConversation.getParticipants().add(p1);
        sampleConversation.getParticipants().add(p2);
    }

    @Test
    @DisplayName("createConversation should return existing conversation if direct conversation already exists")
    void testCreateConversationIdempotency() {
        CreateConversationRequest request = new CreateConversationRequest(
                ConversationType.DIRECT, user2Id, null, null, "Chat", "Hello"
        );

        when(conversationRepository.findDirectConversationBetween(user1Id, user2Id, ConversationType.DIRECT))
                .thenReturn(Optional.of(sampleConversation));
        when(chatMapper.toResponse(sampleConversation)).thenReturn(new ConversationResponse(
                conversationId, ConversationType.DIRECT, null, null, null, "Direct Chat", Instant.now(), null, List.of(), List.of(), Instant.now()
        ));

        ConversationResponse response = conversationService.createConversation(request, user1Id);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(conversationId);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConversation should save new conversation and create participants when not existing")
    void testCreateNewConversation() {
        CreateConversationRequest request = new CreateConversationRequest(
                ConversationType.DIRECT, user2Id, null, null, "New Chat", "Hello"
        );

        when(conversationRepository.findDirectConversationBetween(user1Id, user2Id, ConversationType.DIRECT))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(sampleConversation);
        when(chatMapper.toResponse(sampleConversation)).thenReturn(new ConversationResponse(
                conversationId, ConversationType.DIRECT, null, null, null, "New Chat", Instant.now(), null, List.of(), List.of(), Instant.now()
        ));

        ConversationResponse response = conversationService.createConversation(request, user1Id);

        assertThat(response).isNotNull();
        verify(conversationRepository).save(any(Conversation.class));
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    @DisplayName("getConversationById should throw UnauthorizedChatAccessException if user is not a participant")
    void testGetConversationUnauthorized() {
        UUID strangerId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(sampleConversation));

        assertThatThrownBy(() -> conversationService.getConversationById(conversationId, strangerId))
                .isInstanceOf(UnauthorizedChatAccessException.class);
    }

    @Test
    @DisplayName("getConversationById should return details when user is a participant")
    void testGetConversationSuccess() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(sampleConversation));
        when(chatMapper.toResponse(sampleConversation)).thenReturn(new ConversationResponse(
                conversationId, ConversationType.DIRECT, null, null, null, "Direct Chat", Instant.now(), null, List.of(), List.of(), Instant.now()
        ));

        ConversationResponse response = conversationService.getConversationById(conversationId, user1Id);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(conversationId);
    }
}
