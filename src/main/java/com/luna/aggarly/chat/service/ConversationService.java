package com.luna.aggarly.chat.service;

import com.luna.aggarly.chat.dto.request.CreateConversationRequest;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.ConversationSummaryResponse;
import com.luna.aggarly.chat.dto.response.MessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ConversationService {

    ConversationResponse createConversation(CreateConversationRequest request, UUID creatorId);

    ConversationResponse getOrCreateAiConciergeConversation(UUID userId);

    Page<ConversationSummaryResponse> getUserConversations(UUID userId, Pageable pageable);

    ConversationResponse getConversationById(UUID conversationId, UUID currentUserId);

    List<MessageResponse> getRecentMessages(UUID conversationId, int limit, UUID currentUserId);

    void markConversationAsRead(UUID conversationId, UUID userId, UUID lastReadMessageId);

    void muteConversation(UUID conversationId, UUID userId, boolean muted);

    void archiveConversation(UUID conversationId, UUID userId, boolean archived);
}
