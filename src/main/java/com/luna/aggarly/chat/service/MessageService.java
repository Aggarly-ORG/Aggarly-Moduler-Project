package com.luna.aggarly.chat.service;

import com.luna.aggarly.chat.dto.response.MessageResponse;
import com.luna.aggarly.chat.entity.enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageService {

    MessageResponse sendMessage(UUID conversationId, UUID senderId, String content, MessageType messageType, String metadataJson);

    MessageResponse sendSystemMessage(UUID conversationId, String content, String metadataJson);

    Page<MessageResponse> getConversationMessages(UUID conversationId, UUID currentUserId, Pageable pageable);

    List<MessageResponse> getMessagesBefore(UUID conversationId, Instant before, int limit, UUID currentUserId);

    void recordReadReceipt(UUID messageId, UUID userId);
}
