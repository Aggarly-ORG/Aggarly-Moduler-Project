package com.luna.aggarly.aiagent.tool.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.Message;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.repository.MessageRepository;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadConversationHistoryTool implements Tool<ReadConversationHistoryTool.Params, ReadConversationHistoryTool.Response> {

    public record Params(
            UUID conversationId,
            Integer page,
            Integer size,
            String sortDirection
    ) {
        public int pageOrDefault() {
            return (page != null && page >= 0) ? page : 0;
        }

        public int sizeOrDefault() {
            return (size != null && size > 0 && size <= 100) ? size : 25;
        }

        public boolean isAscending() {
            return sortDirection == null || "ASC".equalsIgnoreCase(sortDirection);
        }
    }

    public record Response(
            UUID conversationId,
            String title,
            String conversationType,
            int page,
            int size,
            long totalMessages,
            int totalPages,
            boolean hasNext,
            List<MessageItem> messages
    ) {
        public record MessageItem(
                UUID messageId,
                UUID senderId,
                String senderName,
                String messageType,
                String content,
                Instant createdAt
        ) {}
    }

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "messaging.readConversation";
    }

    @Override
    public String description() {
        return "Retrieve full pageable message history of any conversation thread (e.g., when tagged by @Lumen or reviewing past guest-host chats). Supports page, size, and chronological order.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Response> execute(Params request, UserPrincipal user) {
        if (request.conversationId() == null) {
            return ToolResult.failed("INVALID_ARGUMENT", "conversationId must not be null");
        }

        Conversation conversation = conversationRepository.findById(request.conversationId()).orElse(null);
        if (conversation == null) {
            return ToolResult.failed("CONVERSATION_NOT_FOUND", "Conversation not found: " + request.conversationId());
        }

        boolean isParticipant = participantRepository.existsByConversationIdAndUserId(
                request.conversationId(), user.getUserId());
        if (!isParticipant) {
            return ToolResult.failed("FORBIDDEN", "User is not a participant in conversation: " + request.conversationId());
        }

        Sort.Direction direction = request.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), Sort.by(direction, "createdAt"));

        Page<Message> messagePage = messageRepository.findByConversationId(
                request.conversationId(), pageRequest);

        List<UUID> senderIds = messagePage.getContent().stream()
                .map(Message::getSenderId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, String> senderNameMap = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> (u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "").trim(),
                        (existing, replacement) -> existing
                ));

        List<Response.MessageItem> messageItems = messagePage.getContent().stream()
                .map(m -> new Response.MessageItem(
                        m.getId(),
                        m.getSenderId(),
                        senderNameMap.getOrDefault(m.getSenderId(), "Unknown"),
                        m.getMessageType() != null ? m.getMessageType().name() : "TEXT",
                        m.getContent(),
                        m.getCreatedAt()
                ))
                .collect(Collectors.toList());

        Response response = new Response(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getType() != null ? conversation.getType().name() : "DIRECT",
                messagePage.getNumber(),
                messagePage.getSize(),
                messagePage.getTotalElements(),
                messagePage.getTotalPages(),
                messagePage.hasNext(),
                messageItems
        );

        return ToolResult.ok(response);
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService != null ? jsonSchemaService.generate(Params.class) : null;
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService != null ? jsonSchemaService.generate(Response.class) : null;
    }
}
