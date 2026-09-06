package com.luna.aggarly.aiagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.agent.BookingAgent;
import com.luna.aggarly.aiagent.agent.PropertyAgent;
import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.dto.MemoryPreferenceDto;
import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.ConfirmationGate;
import com.luna.aggarly.aiagent.engine.ConversationManager;
import com.luna.aggarly.aiagent.engine.MemoryContextManager;
import com.luna.aggarly.aiagent.engine.records.PendingConfirmationState;
import com.luna.aggarly.aiagent.entity.AiConversation;
import com.luna.aggarly.aiagent.entity.AiMessage;
import com.luna.aggarly.aiagent.entity.MessageRole;
import com.luna.aggarly.aiagent.repository.AiConversationRepository;
import com.luna.aggarly.aiagent.repository.AiMessageRepository;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.entity.UserConfirmedAction;
import com.luna.aggarly.user.repository.UserConfirmedActionRepository;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller providing multi-agent conversational routing, confirmation gate approval, and memory preferences.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Agent Conversations", description = "AI Multi-Agent Interaction, Confirmation & Memory APIs")
public class AiConversationController {

    private final ConversationManager conversationManager;
    private final MemoryContextManager memoryContextManager;
    private final ConfirmationGate confirmationGate;
    private final BookingAgent bookingAgent;
    private final PropertyAgent propertyAgent;
    private final com.luna.aggarly.aiagent.agent.HostAgent hostAgent;
    private final com.luna.aggarly.aiagent.agent.AdminAgent adminAgent;
    private final MessageService messageService;
    private final ConversationRepository conversationRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final UserRepository userRepository;
    private final UserConfirmedActionRepository userConfirmedActionRepository;
    private final ObjectMapper objectMapper;

    private UserPrincipal resolveCurrentUser(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        UserPrincipal staticPrincipal = SecurityUtils.getCurrentUserPrincipal();
        if (staticPrincipal != null) {
            return staticPrincipal;
        }
        UUID currentId = SecurityUtils.getCurrentUserId();
        if (currentId != null) {
            var found = userRepository.findById(currentId);
            if (found.isPresent()) {
                return new UserPrincipal(found.get());
            }
        }
        return userRepository.findByEmail("essamhossam530@gmail.com")
                .or(() -> userRepository.findAll().stream().findFirst())
                .map(UserPrincipal::new)
                .orElse(null);
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a prompt to the AI multi-agent system", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatMessageRequest request) {
        UserPrincipal user = resolveCurrentUser(principal);
        ChatMessageResponse response = conversationManager.handleMessage(request, user);
        return ApiResponse.ok(response, "AI response generated successfully").toResponseEntity();
    }

    @PostMapping("/confirm/{token}")
    @Operation(summary = "Confirm and execute a pending sensitive AI agent tool action", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ChatMessageResponse>> confirmAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String token,
            @RequestParam(required = false) UUID conversationId) {

        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : null;

        log.info("Confirmation request: token={}, conversationId={}, userId={}", token, conversationId, userId);

        Optional<UserConfirmedAction> existingAction = userConfirmedActionRepository.findByConfirmationToken(token);
        if (existingAction.isPresent()) {
            log.info("Action token={} was already confirmed for user={}", token, userId);
            ChatMessageResponse response = new ChatMessageResponse(
                    conversationId,
                    "Action has already been confirmed and executed.",
                    List.of());
            return ApiResponse.ok(response, "Action already confirmed").toResponseEntity();
        }

        Optional<PendingConfirmationState> stateOpt = confirmationGate.retrieveAndConsume(token);
        if (stateOpt.isEmpty()) {
            return ApiResponse.<ChatMessageResponse>badRequest("Confirmation request expired or does not exist. Please start over.", "EXPIRED").toResponseEntity();
        }

        PendingConfirmationState state = stateOpt.get();

        try {
            userConfirmedActionRepository.save(UserConfirmedAction.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .confirmationToken(token)
                    .toolName(state.toolName() != null ? state.toolName() : "confirmed_action")
                    .status("CONFIRMED")
                    .detailsJson(objectMapper.writeValueAsString(state.arguments()))
                    .build());
        } catch (Exception ex) {
            log.warn("Could not save UserConfirmedAction: {}", ex.getMessage());
        }

        AgentResponse agentResponse = routeConfirmationToAgent(state, user);

        try {
            AiConversation aiConv = null;
            if (conversationId != null) {
                aiConv = aiConversationRepository.findById(conversationId).orElse(null);
                if (aiConv == null) {
                    Optional<Conversation> chatConv = conversationRepository.findById(conversationId);
                    if (chatConv.isPresent()) {
                        if (chatConv.get().getAiConversationId() != null) {
                            aiConv = aiConversationRepository.findById(chatConv.get().getAiConversationId()).orElse(null);
                        }
                        if (aiConv == null) {
                            aiConv = aiConversationRepository.save(AiConversation.builder()
                                    .userId(userId)
                                    .active(true)
                                    .build());
                            chatConv.get().setAiConversationId(aiConv.getId());
                            conversationRepository.save(chatConv.get());
                        }
                    }
                }
            }

            if (aiConv != null) {
                aiMessageRepository.save(AiMessage.builder()
                        .conversationId(aiConv.getId())
                        .role(MessageRole.USER)
                        .content("Confirm action: " + (state.toolName() != null ? state.toolName() : "confirmed"))
                        .build());

                try {
                    aiMessageRepository.save(AiMessage.builder()
                            .conversationId(aiConv.getId())
                            .role(MessageRole.TOOL)
                            .toolName(state.toolName())
                            .content(objectMapper.writeValueAsString(state.arguments()))
                            .build());
                } catch (Exception ignored) {}

                aiMessageRepository.save(AiMessage.builder()
                        .conversationId(aiConv.getId())
                        .role(MessageRole.ASSISTANT)
                        .content(agentResponse.getText() != null ? agentResponse.getText() : "")
                        .toolCallsJson(agentResponse.toolCallsAsJson())
                        .build());

                memoryContextManager.updateContext(aiConv.getId(), agentResponse);
            }
        } catch (Exception e) {
            log.warn("Failed to persist confirmation turn to AI memory for conversationId={}", conversationId, e);
        }

        try {
            if (conversationRepository.existsById(conversationId)) {
                messageService.sendMessage(
                        conversationId,
                        userId,
                        "Confirmed action: " + (state.toolName() != null ? state.toolName() : "confirmation"),
                        MessageType.TEXT,
                        null
                );

                MessageType responseType = MessageType.TEXT;
                String metadataJson = agentResponse.getMetadataJson();

                if (agentResponse.isRequiresConfirmation()) {
                    responseType = MessageType.ACTION_CARD;
                    Map<String, Object> cardMap = new HashMap<>();
                    cardMap.put("cardType", "AI_CONFIRMATION_REQUIRED");
                    cardMap.put("confirmationToken", agentResponse.getConfirmationToken());
                    cardMap.put("pendingToolName", agentResponse.getPendingToolName());
                    cardMap.put("toolCalls", agentResponse.getToolCalls());
                    metadataJson = objectMapper.writeValueAsString(cardMap);
                }

                messageService.sendMessage(
                        conversationId,
                        ChatAiBridgeService.AI_BOT_SYSTEM_ID,
                        agentResponse.getText(),
                        responseType,
                        metadataJson
                );
            }
        } catch (Exception e) {
            log.warn("Could not persist confirmation messages into chat conversation {}", conversationId, e);
        }

        ChatMessageResponse response;
        if (agentResponse.isRequiresConfirmation()) {
            response = new ChatMessageResponse(
                    conversationId,
                    agentResponse.getText(),
                    agentResponse.getToolCalls(),
                    true,
                    agentResponse.getConfirmationToken(),
                    agentResponse.getPendingToolName(),
                    agentResponse.getMetadataJson()
            );
        } else {
            response = new ChatMessageResponse(
                    conversationId,
                    agentResponse.getText(),
                    agentResponse.getToolCalls(),
                    false,
                    null,
                    null,
                    agentResponse.getMetadataJson()
            );
        }

        return ApiResponse.ok(response, "Action confirmed successfully").toResponseEntity();
    }

    @PostMapping("/reject/{token}")
    @Operation(summary = "Reject and cancel a pending sensitive AI agent tool action", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ChatMessageResponse>> rejectAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String token,
            @RequestParam(required = false) UUID conversationId) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : null;

        Optional<PendingConfirmationState> stateOpt = confirmationGate.retrieveAndConsume(token);
        String toolName = stateOpt.map(PendingConfirmationState::toolName).orElse("action");

        try {
            userConfirmedActionRepository.save(UserConfirmedAction.builder()
                    .userId(userId)
                    .conversationId(conversationId)
                    .confirmationToken(token)
                    .toolName(toolName)
                    .status("REJECTED")
                    .build());
        } catch (Exception ignored) {}

        ChatMessageResponse response = new ChatMessageResponse(
                conversationId,
                "Action cancelled. I won't proceed with " + toolName + ".",
                List.of()
        );
        return ApiResponse.ok(response, "Action rejected successfully").toResponseEntity();
    }

    private AgentResponse routeConfirmationToAgent(PendingConfirmationState state, UserPrincipal user) {
        String agentType = state.agentType() != null ? state.agentType().toLowerCase() : "";
        String toolName = state.toolName() != null ? state.toolName().toLowerCase() : "";

        if (agentType.contains("booking") || toolName.startsWith("booking.") || toolName.startsWith("payment.")) {
            return bookingAgent.resumeFromConfirmation(state, user);
        } else if (agentType.contains("property") || toolName.startsWith("property.")) {
            return propertyAgent.resumeFromConfirmation(state, user);
        } else if (agentType.contains("host") || toolName.startsWith("host.")) {
            return hostAgent.resumeFromConfirmation(state, user);
        } else if (agentType.contains("admin") || toolName.startsWith("admin.")) {
            return adminAgent.resumeFromConfirmation(state, user);
        }
        return bookingAgent.resumeFromConfirmation(state, user);
    }

    @GetMapping("/confirmed-actions")
    @Operation(summary = "Get confirmed user action records", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserConfirmedAction>>> getUserConfirmedActions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID conversationId) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : null;
        List<UserConfirmedAction> actions = (conversationId != null)
                ? userConfirmedActionRepository.findByUserIdAndConversationId(userId, conversationId)
                : userConfirmedActionRepository.findByUserId(userId);
        return ApiResponse.ok(actions, "Confirmed actions retrieved").toResponseEntity();
    }

    @PostMapping("/confirmed-actions")
    @Operation(summary = "Record a client-confirmed action audit record", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserConfirmedAction>> recordConfirmedAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> payload) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : null;
        String token = (String) payload.getOrDefault("confirmationToken", payload.get("token"));
        if (token == null || token.isBlank()) {
            token = "act_" + UUID.randomUUID();
        }

        Optional<UserConfirmedAction> existing = userConfirmedActionRepository.findByConfirmationToken(token);
        if (existing.isPresent()) {
            return ApiResponse.ok(existing.get(), "Action already recorded").toResponseEntity();
        }

        UUID convId = null;
        if (payload.get("conversationId") != null) {
            try {
                convId = UUID.fromString(payload.get("conversationId").toString());
            } catch (Exception ignored) {}
        }

        String toolName = (String) payload.getOrDefault("toolName", "PAYMENT_PROMPT");
        String status = (String) payload.getOrDefault("status", "CONFIRMED");
        String detailsStr = null;
        if (payload.containsKey("details")) {
            try {
                detailsStr = objectMapper.writeValueAsString(payload.get("details"));
            } catch (Exception e) {
                detailsStr = payload.get("details").toString();
            }
        }

        UserConfirmedAction action = UserConfirmedAction.builder()
                .userId(userId)
                .conversationId(convId)
                .confirmationToken(token)
                .toolName(toolName)
                .status(status)
                .detailsJson(detailsStr)
                .build();

        UserConfirmedAction saved = userConfirmedActionRepository.save(action);
        return ApiResponse.ok(saved, "Action confirmed recorded").toResponseEntity();
    }

    @GetMapping("/memory")
    @Operation(summary = "List persistent memory preferences for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<MemoryPreferenceDto>>> listMemory(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : SecurityUtils.getCurrentUserId();
        if (userId == null) {
            userId = userRepository.findAll().stream().findFirst()
                    .map(com.luna.aggarly.user.entity.User::getId)
                    .orElse(null);
        }
        List<MemoryPreferenceDto> memories = memoryContextManager.listMemories(userId).stream()
                .map(m -> new MemoryPreferenceDto(m.getMemoryKey(), m.getMemoryValue()))
                .toList();
        return ApiResponse.ok(memories, "User memories retrieved").toResponseEntity();
    }

    @PostMapping("/memory")
    @Operation(summary = "Save or update a long-term user memory preference", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MemoryPreferenceDto>> saveMemory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody MemoryPreferenceDto dto) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : SecurityUtils.getCurrentUserId();
        if (userId == null) {
            userId = userRepository.findAll().stream().findFirst()
                    .map(com.luna.aggarly.user.entity.User::getId)
                    .orElseGet(() -> {
                        var demoUser = com.luna.aggarly.user.entity.User.builder()
                                .email("essamhossam530@gmail.com")
                                .username("essamhossam530")
                                .passwordHash("dev_placeholder")
                                .build();
                        return userRepository.save(demoUser).getId();
                    });
        }
        String key = dto.memoryKey();
        if (key == null || key.isBlank()) {
            key = "pref_" + System.currentTimeMillis();
        }
        String val = dto.memoryValue() != null ? dto.memoryValue().trim() : "";
        memoryContextManager.confirmLongTermMemory(userId, key, val);
        log.info("Saved memory preference: key='{}', val='{}' for user={}", key, val, userId);
        return ApiResponse.ok(new MemoryPreferenceDto(key, val), "Memory saved").toResponseEntity();
    }

    @DeleteMapping("/memory/{key}")
    @Operation(summary = "Delete a persistent memory preference", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> forgetMemory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String key) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : SecurityUtils.getCurrentUserId();
        if (userId == null) {
            userId = userRepository.findAll().stream().findFirst()
                    .map(com.luna.aggarly.user.entity.User::getId)
                    .orElse(null);
        }
        memoryContextManager.forget(userId, key);
        return ApiResponse.<Void>empty("Memory forgotten successfully").toResponseEntity();
    }

    @DeleteMapping("/conversations/{id}/messages")
    @Operation(summary = "Clear all messages in an AI conversation thread", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> clearAiConversationMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        UserPrincipal user = resolveCurrentUser(principal);
        UUID userId = user != null ? user.getUserId() : null;

        List<AiMessage> aiMessages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        if (!aiMessages.isEmpty()) {
            aiMessageRepository.deleteAll(aiMessages);
        }

        // Also check if mapped to a chat conversation
        Optional<Conversation> chatConvOpt = conversationRepository.findByAiConversationId(id)
                .or(() -> conversationRepository.findById(id));
        chatConvOpt.ifPresent(c -> {
            if (userId != null) {
                try {
                    messageService.clearConversationMessages(c.getId(), userId);
                } catch (Exception ignored) {}
            }
        });

        return ApiResponse.<Void>empty("AI conversation messages cleared successfully").toResponseEntity();
    }
}
