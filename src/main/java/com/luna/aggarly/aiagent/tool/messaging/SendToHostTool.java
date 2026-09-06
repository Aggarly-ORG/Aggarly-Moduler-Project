package com.luna.aggarly.aiagent.tool.messaging;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.chat.dto.request.CreateConversationRequest;
import com.luna.aggarly.chat.dto.response.ConversationResponse;
import com.luna.aggarly.chat.dto.response.ConversationSummaryResponse;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.service.ConversationService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SendToHostTool implements Tool<SendToHostTool.Params, Map<String, Object>> {

    private static final int CONVERSATION_SCAN_LIMIT = 50;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    public SendToHostTool(
            ConversationService conversationService,
            @Lazy MessageService messageService,
            PropertyService propertyService,
            JsonSchemaService jsonSchemaService
    ) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.propertyService = propertyService;
        this.jsonSchemaService = jsonSchemaService;
    }

    public record Params(
            @JsonPropertyDescription("The message text to deliver to the host, composed from the guest's intent.")
            String body,

            @JsonPropertyDescription("UUID of an existing chat conversation to send into. Omit when starting fresh.")
            UUID conversationId,

            @JsonPropertyDescription("UUID of the property the guest is asking about; resolves and reaches the host. " +
                    "Required unless conversationId is provided.")
            UUID propertyId,

            @JsonPropertyDescription("Optional booking UUID to link a new conversation to a specific reservation.")
            UUID bookingId
    ) {}

    @Override
    public String name() {
        return "messaging.sendToHost";
    }

    @Override
    public String description() {
        return "Deliver a message from the authenticated guest to a property's host through the platform chat. " +
                "Reuses the existing guest-host conversation for that property when one exists, otherwise starts one. " +
                "Use for requests like 'ask the host' or 'message the host about early check-in'.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        if (user == null || user.getUserId() == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to message hosts.");
        }
        if (params.body() == null || params.body().isBlank()) {
            return ToolResult.failed("EMPTY_MESSAGE", "A non-empty 'body' is required.");
        }
        if (params.conversationId() == null && params.propertyId() == null) {
            return ToolResult.failed("MISSING_TARGET",
                    "Provide either a conversationId or a propertyId to resolve the host.");
        }

        UUID guestId = user.getUserId();
        try {
            UUID conversationId = resolveConversation(params, guestId);
            messageService.sendMessage(conversationId, guestId, params.body().trim(), MessageType.TEXT, null);

            return ToolResult.ok(Map.of(
                    "conversationId", conversationId,
                    "delivered", true,
                    "message", "Your message was delivered to the host."
            ));
        } catch (Exception ex) {
            log.error("Failed to deliver guest message to host (user={}, property={})", guestId, params.propertyId(), ex);
            return ToolResult.failed("MESSAGE_DELIVERY_FAILED", ex.getMessage());
        }
    }

    private UUID resolveConversation(Params params, UUID guestId) {
        if (params.conversationId() != null) {
            conversationService.getConversationById(params.conversationId(), guestId);
            return params.conversationId();
        }

        PropertyResponse property = propertyService.getPropertyById(params.propertyId());
        UUID hostId = property.hostId();
        if (hostId == null) {
            throw new IllegalStateException("Property has no host assigned.");
        }

        UUID existing = findExistingConversation(guestId, hostId, params);
        if (existing != null) {
            return existing;
        }

        ConversationType type = params.bookingId() != null ? ConversationType.BOOKING_INQUIRY : ConversationType.DIRECT;
        String title = "Inquiry: " + (property.title() != null ? property.title() : "Property");
        ConversationResponse created = conversationService.createConversation(
                new CreateConversationRequest(type, hostId, params.propertyId(), params.bookingId(), title, null),
                guestId
        );
        return created.id();
    }

    private UUID findExistingConversation(UUID guestId, UUID hostId, Params params) {
        Pageable scan = PageRequest.of(0, CONVERSATION_SCAN_LIMIT);
        for (ConversationSummaryResponse conv : conversationService.getUserConversations(guestId, scan).getContent()) {
            if (conv.type() == ConversationType.AI_CONCIERGE) continue;
            if (params.propertyId() != null && !params.propertyId().equals(conv.propertyId())) continue;
            boolean hostIsParticipant = conv.participants() != null && conv.participants().stream()
                    .anyMatch(p -> hostId.equals(p.userId()));
            if (hostIsParticipant) {
                return conv.id();
            }
        }
        return null;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
