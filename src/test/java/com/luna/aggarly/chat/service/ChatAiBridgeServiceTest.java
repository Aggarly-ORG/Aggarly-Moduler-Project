package com.luna.aggarly.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.engine.ConversationManager;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.service.impl.ChatAiBridgeServiceImpl;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatAiBridgeServiceTest {

    @Mock
    private ConversationManager aiConversationManager;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatAiBridgeServiceImpl chatAiBridgeService;

    private UUID conversationId;
    private UUID userId;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        chatAiBridgeService.setMessageService(messageService);
        conversationId = UUID.randomUUID();
        userId = UUID.randomUUID();

        sampleUser = User.builder()
                .email("user@example.com")
                .roles(new HashSet<>())
                .build();
        sampleUser.setId(userId);
    }

    @Test
    @DisplayName("processAiChatTurnAsync should call ConversationManager and deliver AI bot message")
    void testProcessAiChatTurnAsync() {
        ChatMessageResponse aiResponse = new ChatMessageResponse(
                conversationId,
                "I found 3 great properties matching your criteria!",
                List.of()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(aiConversationManager.handleMessage(any(ChatMessageRequest.class), any())).thenReturn(aiResponse);

        chatAiBridgeService.processAiChatTurnAsync(conversationId, userId, "Search apartments in Paris");

        verify(aiConversationManager).handleMessage(any(ChatMessageRequest.class), any());
        verify(messageService).sendMessage(
                eq(conversationId),
                eq(ChatAiBridgeService.AI_BOT_SYSTEM_ID),
                eq("I found 3 great properties matching your criteria!"),
                eq(MessageType.TEXT),
                isNull()
        );
    }
}
