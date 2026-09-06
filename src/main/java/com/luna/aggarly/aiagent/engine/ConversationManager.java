package com.luna.aggarly.aiagent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.ChatMessageRequest;
import com.luna.aggarly.aiagent.dto.ChatMessageResponse;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.entity.AiConversation;
import com.luna.aggarly.aiagent.entity.AiMessage;
import com.luna.aggarly.aiagent.entity.MessageRole;
import com.luna.aggarly.aiagent.repository.AiConversationRepository;
import com.luna.aggarly.aiagent.repository.AiMessageRepository;
import com.luna.aggarly.chat.entity.Conversation;
import com.luna.aggarly.chat.entity.ConversationParticipant;
import com.luna.aggarly.chat.entity.enums.ConversationType;
import com.luna.aggarly.chat.entity.enums.MessageType;
import com.luna.aggarly.chat.entity.enums.ParticipantRole;
import com.luna.aggarly.chat.repository.ConversationParticipantRepository;
import com.luna.aggarly.chat.repository.ConversationRepository;
import com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher;
import com.luna.aggarly.chat.service.ChatAiBridgeService;
import com.luna.aggarly.chat.service.MessageService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class ConversationManager {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final MemoryContextManager memoryContextManager;
    private final IntentClassifier intentClassifier;
    private final AgentRouter agentRouter;
    private final ConversationRepository chatConversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ObjectMapper objectMapper;
    private final com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher;
    private MessageService messageService;

    public ConversationManager(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            MemoryContextManager memoryContextManager,
            IntentClassifier intentClassifier,
            AgentRouter agentRouter,
            ConversationRepository chatConversationRepository,
            ConversationParticipantRepository participantRepository,
            ObjectMapper objectMapper,
            com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.memoryContextManager = memoryContextManager;
        this.intentClassifier = intentClassifier;
        this.agentRouter = agentRouter;
        this.chatConversationRepository = chatConversationRepository;
        this.participantRepository = participantRepository;
        this.objectMapper = objectMapper;
        this.activityPublisher = activityPublisher;
    }

    @Autowired
    public void setMessageService(@Lazy MessageService messageService) {
        this.messageService = messageService;
    }

    @Transactional
    public ChatMessageResponse handleMessage(ChatMessageRequest request, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : UUID.randomUUID();
        log.info("Handling chat message for userId={}, conversationId={}", userId, request.conversationId());

        long overallStartTime = System.currentTimeMillis();
        AgentActivityPublisher.startRecording();

        try {
            AiConversation conversation = resolveOrCreateConversation(request.conversationId(), userId);

            UUID chatConvId = request.chatConversationId();
            if (chatConvId == null) {
                Optional<Conversation> chatConvOpt = chatConversationRepository.findByAiConversationId(conversation.getId());
                if (chatConvOpt.isPresent()) {
                    chatConvId = chatConvOpt.get().getId();
                } else if (request.conversationId() != null) {
                    Optional<Conversation> direct = chatConversationRepository.findById(request.conversationId());
                    if (direct.isPresent()) {
                        chatConvId = direct.get().getId();
                    }
                }
            }

            UUID effectiveBroadcastId = chatConvId != null ? chatConvId : conversation.getId();

            long thinkStart = System.currentTimeMillis();
            activityPublisher.publishThinkingStart(effectiveBroadcastId, request.content());

            // 1. Assign smart meaningful title if empty or default
            if (conversation.getTitle() == null || conversation.getTitle().isBlank() || conversation.getTitle().equals("New AI Inquiry") || conversation.getTitle().equals("Aggarly AI Concierge")) {
                String title = generateTitle(request.content());
                conversation.setTitle(title);
                conversationRepository.save(conversation);
            }

            persistUserMessage(conversation.getId(), request.content());

            ConversationContext context = memoryContextManager.loadContext(conversation.getId(), userId);
            context = new ConversationContext(effectiveBroadcastId, context.activeSearchContext(), context.userMemories(), context.conversationHistory());

            activityPublisher.publishThinkingEnd(effectiveBroadcastId, System.currentTimeMillis() - thinkStart, "Context & memories loaded (" + (context.conversationHistory() != null ? context.conversationHistory().size() : 0) + " messages in history)");

            long intentStart = System.currentTimeMillis();
            activityPublisher.publishIntentStart(effectiveBroadcastId, request.content());

            ClassifiedIntent intent = intentClassifier.classify(request.content(), context);
            String agentName = resolveAgentNameForCategory(intent.category());
            activityPublisher.publishIntentEnd(effectiveBroadcastId, intent.category().name(), 0.95, agentName, System.currentTimeMillis() - intentStart);

            AgentResponse response = agentRouter.route(intent, context, user);

            // Enrich response with the complete timeline of recorded events (Thinking, Intent, Turns, Tools, Synthesis, Completion)
            List<com.luna.aggarly.aiagent.dto.activity.AgentActivityEvent> recordedEvents = AgentActivityPublisher.getRecordedEvents();
            if (!recordedEvents.isEmpty() && response != null && response.getText() != null) {
                Map<String, Object> traceData = new LinkedHashMap<>();
                traceData.put("title", "Execution Plan & Live Trace");
                traceData.put("totalDurationMs", System.currentTimeMillis() - overallStartTime);
                traceData.put("totalSteps", recordedEvents.size());

                List<Map<String, Object>> sanitizedEvents = new ArrayList<>();
                List<Map<String, Object>> stepsList = new ArrayList<>();

                for (com.luna.aggarly.aiagent.dto.activity.AgentActivityEvent ev : recordedEvents) {
                    Map<String, Object> evMap = new LinkedHashMap<>();
                    evMap.put("id", ev.id());
                    evMap.put("eventType", ev.eventType());
                    evMap.put("activityType", ev.activityType() != null ? ev.activityType().name() : null);
                    evMap.put("agentName", ev.agentName());
                    evMap.put("turn", ev.turn());
                    evMap.put("toolName", ev.toolName());
                    evMap.put("friendlyTitle", ev.friendlyTitle());
                    evMap.put("status", ev.status());
                    evMap.put("durationMs", ev.durationMs());
                    evMap.put("inputSummary", ev.inputSummary());
                    evMap.put("resultSummary", ev.resultSummary() != null ? ev.resultSummary() : ev.friendlyTitle());
                    evMap.put("timestamp", ev.timestamp());
                    sanitizedEvents.add(evMap);

                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("id", ev.id());
                    step.put("activityType", ev.activityType() != null ? ev.activityType().name() : null);
                    step.put("agentName", ev.agentName());
                    step.put("toolName", ev.toolName());
                    step.put("friendlyTitle", ev.friendlyTitle());
                    step.put("status", ev.status());
                    step.put("durationMs", ev.durationMs());
                    step.put("input", ev.inputSummary());
                    step.put("summary", ev.resultSummary() != null ? ev.resultSummary() : ev.friendlyTitle());
                    step.put("timestamp", ev.timestamp());
                    stepsList.add(step);
                }

                traceData.put("events", sanitizedEvents);
                traceData.put("steps", stepsList);

                List<com.luna.aggarly.aiagent.engine.model.LumenResponseBlock> extraBlocks = new ArrayList<>();
                extraBlocks.add(com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.executionPlan(traceData));

                if (response.getMetadataJson() != null && !response.getMetadataJson().isBlank()) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metaMap = objectMapper.readValue(response.getMetadataJson(), Map.class);
                        List<com.luna.aggarly.aiagent.engine.model.LumenResponseBlock> metaBlocks = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.extractBlocksFromMetadata(metaMap);
                        extraBlocks.addAll(metaBlocks);
                    } catch (Exception metaEx) {
                        log.debug("Failed to extract blocks from agent metadataJson: {}", metaEx.getMessage());
                    }
                }

                if (response.isRequiresConfirmation() && response.getConfirmationToken() != null) {
                    Map<String, Object> confData = new LinkedHashMap<>();
                    confData.put("title", "Action Confirmation Required");
                    confData.put("message", response.getText() != null && !response.getText().startsWith("{") ? response.getText() : "This operation requires your explicit confirmation.");
                    confData.put("toolName", response.getPendingToolName());
                    confData.put("pendingToolName", response.getPendingToolName());
                    confData.put("confirmationToken", response.getConfirmationToken());
                    confData.put("confirmEndpoint", "/api/v1/ai/confirm/" + response.getConfirmationToken());
                    confData.put("rejectEndpoint", "/api/v1/ai/reject/" + response.getConfirmationToken());
                    confData.put("actionType", response.getPendingToolName() != null ? response.getPendingToolName().toUpperCase().replace('.', '_') : "CONFIRM_ACTION");
                    confData.put("confirmLabel", "Confirm & Proceed");
                    confData.put("cancelLabel", "Cancel");
                    extraBlocks.add(com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.confirmation(confData));

                    extraBlocks.add(com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.actions(List.of(
                            Map.of("id", "confirm-" + response.getConfirmationToken(), "label", "Confirm & Proceed", "variant", "primary", "action", "ai.confirm", "confirmationToken", response.getConfirmationToken()),
                            Map.of("id", "cancel-" + response.getConfirmationToken(), "label", "Cancel", "variant", "secondary", "action", "ai.cancel", "confirmationToken", response.getConfirmationToken())
                    )));
                }

                String enrichedJson = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.formatResponse(
                        response.getText(),
                        extraBlocks
                );

                response = AgentResponse.builder()
                        .text(enrichedJson)
                        .toolCalls(response.getToolCalls())
                        .requiresConfirmation(response.isRequiresConfirmation())
                        .confirmationToken(response.getConfirmationToken())
                        .pendingToolName(response.getPendingToolName())
                        .metadataJson(response.getMetadataJson())
                        .build();
            }

            persistAssistantMessage(conversation.getId(), response);

            memoryContextManager.updateContext(conversation.getId(), response);

            // 2. Synchronize and Bridge with Chat Module (conversations and messages tables)
            bridgeWithChatModule(conversation, request.content(), response, userId, request.conversationId());

            return buildChatMessageResponse(conversation.getId(), response);
        } finally {
            AgentActivityPublisher.clearRecording();
        }
    }

    private void bridgeWithChatModule(AiConversation aiConv, String userContent, AgentResponse response, UUID userId, UUID requestConvId) {
        try {
            // Find existing linked Chat Conversation
            Optional<Conversation> chatConvOpt = chatConversationRepository.findByAiConversationId(aiConv.getId());
            if (chatConvOpt.isEmpty() && requestConvId != null) {
                chatConvOpt = chatConversationRepository.findById(requestConvId);
            }

            Conversation chatConv = chatConvOpt.orElseGet(() -> {
                Conversation newChat = Conversation.builder()
                        .type(ConversationType.AI_CONCIERGE)
                        .title(aiConv.getTitle() != null ? aiConv.getTitle() : "Lumen AI Concierge")
                        .aiConversationId(aiConv.getId())
                        .lastMessageAt(Instant.now())
                        .lastMessagePreview(userContent.length() > 60 ? userContent.substring(0, 57) + "..." : userContent)
                        .build();
                Conversation saved = chatConversationRepository.save(newChat);

                // Add Guest Participant
                participantRepository.save(ConversationParticipant.builder()
                        .conversation(saved)
                        .userId(userId)
                        .role(ParticipantRole.GUEST)
                        .unreadCount(0)
                        .lastReadAt(Instant.now())
                        .build());

                // Add AI Bot Participant
                participantRepository.save(ConversationParticipant.builder()
                        .conversation(saved)
                        .userId(ChatAiBridgeService.AI_BOT_SYSTEM_ID)
                        .role(ParticipantRole.AI_BOT)
                        .unreadCount(0)
                        .build());

                return saved;
            });

            // Update title and timestamp in Chat Conversation
            chatConv.setAiConversationId(aiConv.getId());
            if (aiConv.getTitle() != null && !aiConv.getTitle().isBlank()) {
                chatConv.setTitle(aiConv.getTitle());
            }
            chatConv.setLastMessageAt(Instant.now());
            chatConv.setLastMessagePreview(response.getText() != null && response.getText().length() > 60
                    ? response.getText().substring(0, 57) + "..."
                    : (response.getText() != null ? response.getText() : "Response generated"));
            chatConversationRepository.save(chatConv);

            // Persist Assistant response into chat messages & broadcast over STOMP WebSocket
            if (messageService != null) {
                MessageType responseType = MessageType.TEXT;
                String metadataJson = response.getMetadataJson();

                if (response.isRequiresConfirmation()) {
                    responseType = MessageType.ACTION_CARD;
                    Map<String, Object> cardMap = new HashMap<>();
                    cardMap.put("cardType", "AI_CONFIRMATION_REQUIRED");
                    cardMap.put("confirmationToken", response.getConfirmationToken());
                    cardMap.put("pendingToolName", response.getPendingToolName());
                    cardMap.put("toolCalls", response.getToolCalls());
                    metadataJson = objectMapper.writeValueAsString(cardMap);
                }

                messageService.sendMessage(
                        chatConv.getId(),
                        ChatAiBridgeService.AI_BOT_SYSTEM_ID,
                        response.getText(),
                        responseType,
                        metadataJson
                );
                log.info("Successfully bridged AI response to chat conversation {}", chatConv.getId());
            }
        } catch (Exception e) {
            log.warn("Could not bridge AI conversation {} to chat module: {}", aiConv.getId(), e.getMessage());
        }
    }

    private String generateTitle(String content) {
        if (content == null || content.isBlank()) {
            return "Trip Planning Inquiry";
        }
        String cleaned = content.replaceAll("[\"'\n\r]", " ").trim();
        if (cleaned.toLowerCase().startsWith("confirm action") || cleaned.toLowerCase().startsWith("confirmed action")) {
            return "Booking Confirmation";
        }
        if (cleaned.length() > 45) {
            return cleaned.substring(0, 42) + "...";
        }
        return cleaned;
    }

    private ChatMessageResponse buildChatMessageResponse(UUID conversationId, AgentResponse response) {
        if (response.isRequiresConfirmation()) {
            return new ChatMessageResponse(
                    conversationId,
                    response.getText(),
                    response.getToolCalls(),
                    true,
                    response.getConfirmationToken(),
                    response.getPendingToolName(),
                    response.getMetadataJson()
            );
        }
        return new ChatMessageResponse(
                conversationId,
                response.getText(),
                response.getToolCalls(),
                false,
                null,
                null,
                response.getMetadataJson()
        );
    }

    private AiConversation resolveOrCreateConversation(UUID conversationId, UUID userId) {
        if (conversationId != null) {
            Optional<AiConversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent()) {
                return existing.get();
            }

            // Maybe conversationId is a chat Conversation ID
            Optional<Conversation> chatConv = chatConversationRepository.findById(conversationId);
            if (chatConv.isPresent()) {
                if (chatConv.get().getAiConversationId() != null) {
                    Optional<AiConversation> linkedAiConv = conversationRepository.findById(chatConv.get().getAiConversationId());
                    if (linkedAiConv.isPresent()) {
                        return linkedAiConv.get();
                    }
                }
                // Create new AI conversation and link it
                AiConversation newAiConv = conversationRepository.save(
                        AiConversation.builder()
                                .userId(userId)
                                .active(true)
                                .title(chatConv.get().getTitle())
                                .build()
                );
                chatConv.get().setAiConversationId(newAiConv.getId());
                chatConversationRepository.save(chatConv.get());
                return newAiConv;
            }
        }
        return createNewConversation(userId);
    }

    private AiConversation createNewConversation(UUID userId) {
        return conversationRepository.save(
                AiConversation.builder()
                        .userId(userId)
                        .active(true)
                        .title("Trip Planning Inquiry")
                        .build()
        );
    }

    private void persistUserMessage(UUID conversationId, String content) {
        messageRepository.save(AiMessage.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(content)
                .build());
    }

    private void persistAssistantMessage(UUID conversationId, AgentResponse response) {
        messageRepository.save(AiMessage.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content(response.getText() != null ? response.getText() : "")
                .toolCallsJson(response.toolCallsAsJson())
                .build());
    }

    private String resolveAgentNameForCategory(com.luna.aggarly.aiagent.engine.enums.IntentCategory category) {
        if (category == null) return "PropertyAgent";
        return switch (category) {
            case PROPERTY_SEARCH, PROPERTY_QUESTION, PROPERTY_COMPARISON, AVAILABILITY_QUESTION, IMAGE_ANALYSIS, MEMORY_MANAGEMENT, PLATFORM_NAVIGATION, EXPLAIN_DECISION, SMALL_TALK -> "PropertyAgent";
            case BOOKING_ACTION, REVIEW_REQUEST, NOTIFICATION_REQUEST, DOCUMENT_ANALYSIS -> "BookingAgent";
            case HOST_MANAGEMENT -> "HostAgent";
            case TRAVEL_PLANNING -> "TravelAgent";
            case SUPPORT_QUESTION, MESSAGING_ASSIST -> "SupportAgent";
            case ADMIN_MANAGEMENT, MULTI_STEP_COMPLEX -> "AdminAgent";
            case SCHEDULE_AUTOMATION -> "SchedulingAgent";
        };
    }
}
