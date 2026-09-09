package com.luna.aggarly.aiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.ConfirmationGate;
import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.model.LumenResponseBlock;
import com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter;
import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.engine.records.LlmToolCall;
import com.luna.aggarly.aiagent.engine.records.LlmToolCallResponse;
import com.luna.aggarly.aiagent.engine.records.ToolDefinition;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class VisionAgent implements Agent {

    private static final int MAX_AGENT_TURNS = 6;

    private static final String VISION_AGENT_SYSTEM_PROMPT = """
        You are Aggarly's Multimodal Vision & Aesthetic Concierge AI Assistant.

        You specialize in visual discovery, image-based search, architectural style matching, room coverage inspection, and photo aesthetic comparison for luxury residences and nocturnal sanctuaries.

        ================================================================
        CORE PRINCIPLE & SOURCE OF TRUTH
        ================================================================
        The backend vision tools are the authoritative source of truth for:
        - Visual search and image similarity matches
        - Room coverage and scene classification (pool, bedroom, terrace, observatory, view)
        - Verified visual amenities (infinity pools, stargazing decks, private mooring, tidal pools)
        - Perceptual quality scores, style tags, and visual similarity percentages

        NEVER invent, guess, or assume visual features. Use the tools provided.

        ================================================================
        TOOL USAGE & EXECUTION
        ================================================================
        When a tool is required, return a structured tool call:
        {
          "tool_calls": [
            {
              "name": "<exact_tool_name>",
              "arguments": {
                "<parameter>": <value>
              }
            }
          ]
        }

        Supported Vision Tools:
        1. vision.search: Multimodal visual search. Modes:
           - "TEXT": semantic aesthetic query (e.g. "minimalist brutalist cliff villa with dark-sky terrace")
           - "IMAGE": visual similarity match to an uploaded reference photo via 'referenceObjectKey'
           - "MULTIMODAL": image key + text refinement combined
           - "SIMILAR_PROPERTY": find properties stylistically similar to a referencePropertyId UUID
        2. vision.compareProperties: Compares visual profiles and scenes across multiple property IDs.
        3. vision.getPropertyVisualProfile: Retrieves aesthetic profile, scene breakdown, and visual quality scores for a property.
        4. vision.getImageMetadata: Inspects labels, objects, and scene classification for a specific image key.
        5. vision.extractVisualPreferences: Infers user aesthetic tastes from image interactions.
        6. vision.analyzeReferenceImage: Deep visual breakdown of an uploaded image.
        7. vision.getPhotoTour: Generates an architectural room-by-room photo walkthrough.

        ================================================================
        LUMEN AI — AGENT RESPONSE PRESENTATION FORMAT
        ================================================================
        The final response from the agent MUST be valid JSON conforming to the Lumen Presentation Contract.
        Do NOT wrap JSON inside ```json fences.

        Structure:
        {
          "version": "1",
          "blocks": [
            {
              "type": "text",
              "content": "\\"An evocative opening quote capturing the visual aesthetic.\\"\\n\\nHelpful concierge narrative explaining the visual findings and architectural qualities."
            },
            {
              "type": "property_list",
              "data": {
                "properties": []
              }
            },
            {
              "type": "actions",
              "items": [
                {
                  "label": "Open Property Page",
                  "action": "property.open_page",
                  "parameters": { "propertyId": "<id>" }
                },
                {
                  "label": "Check Availability",
                  "action": "property.availability",
                  "parameters": { "propertyId": "<id>" }
                },
                {
                  "label": "Take Photo Tour",
                  "action": "vision.getPhotoTour",
                  "parameters": { "propertyId": "<id>" }
                }
              ]
            }
          ]
        }

        Supported Block Types:
        - "text": Natural language communication with evocative opening quote.
        - "vision_results": Visual search results card showing match scores, scene tags, and photo previews.
        - "property_list": Curated carousel of matching luxury properties with cover photos, nightly rates, and ratings.
        - "property": Detailed showcase of a single sanctuary.
        - "photo_tour_preview": Architectural walkthrough with scenes, amenity tags, and visual summaries.
        - "compare": Side-by-side aesthetic comparison between 2 or more sanctuaries.
        - "actions": Recommended next actions (Open Property Page, Check Availability, Take Photo Tour).
        - "html": Sandboxed custom HTML widget when interactive or custom visual explorer is requested.

        ================================================================
        RECOMMENDED NEXT ACTIONS
        ================================================================
        Always include an "actions" block suggesting intuitive next steps:
        - "Open Property Page" (`property.open_page` with propertyId)
        - "Check Availability" (`property.availability` with propertyId)
        - "Explore Photo Tour" (`vision.getPhotoTour` with propertyId)
        - "Find Visually Similar" (`vision.search` with referencePropertyId)

        ================================================================
        RUNTIME EXPRESSIONS & UTILITIES
        ================================================================
        You have access to Aggarly's Runtime Expression Engine:
        - {{today()}}, {{now()}}, {{property('<id>').title}}, {{format_currency(amount, 'EUR')}}
        """;

    private final LlmClient llmClient;
    private final ConfirmationGate confirmationGate;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final PropertyService propertyService;
    private final com.luna.aggarly.common.expression.ExpressionEngine expressionEngine;
    private final Map<String, Tool<?, ?>> toolRegistry = new HashMap<>();

    public static final Set<String> SUPPORTED_TOOLS = Set.of(
            "vision.search",
            "vision.compareProperties",
            "vision.getPropertyVisualProfile",
            "vision.getImageMetadata",
            "vision.extractVisualPreferences",
            "vision.analyzeReferenceImage",
            "vision.getPhotoTour"
    );

    private final com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher;

    @Autowired
    public VisionAgent(
            LlmClient llmClient,
            ConfirmationGate confirmationGate,
            ObjectMapper objectMapper,
            Validator validator,
            @Autowired(required = false) PropertyService propertyService,
            @Autowired(required = false) com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher,
            @Autowired(required = false) com.luna.aggarly.common.expression.ExpressionEngine expressionEngine,
            @Qualifier("toolRegistry") Map<String, Tool<?, ?>> sharedToolRegistry
    ) {
        this.llmClient = llmClient;
        this.confirmationGate = confirmationGate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.propertyService = propertyService;
        this.activityPublisher = activityPublisher;
        this.expressionEngine = expressionEngine;

        if (sharedToolRegistry != null) {
            for (String toolName : SUPPORTED_TOOLS) {
                Tool<?, ?> tool = sharedToolRegistry.get(toolName);
                if (tool != null) {
                    this.toolRegistry.put(toolName, tool);
                } else {
                    log.warn("VisionAgent: declared tool '{}' has no registered implementation", toolName);
                }
            }
        }

        log.info("VisionAgent initialized with {} vision domain tools: {}", toolRegistry.size(), toolRegistry.keySet());
    }

    @Override
    public String name() {
        return "VisionAgent";
    }

    @Override
    public String description() {
        return "Multimodal AI Concierge specialized in visual search, image comparison, aesthetic matching, and photo analysis.";
    }

    @Override
    public boolean supports(IntentCategory category) {
        return category == IntentCategory.IMAGE_ANALYSIS;
    }

    @Override
    public List<String> supportedTools() {
        return new ArrayList<>(toolRegistry.keySet());
    }

    @Override
    public AgentResponse handle(ClassifiedIntent intent, ConversationContext context, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : SecurityUtils.getCurrentUserId();
        log.info("VisionAgent handling intent: user={}", userId);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(VISION_AGENT_SYSTEM_PROMPT));

        if (context != null && context.conversationHistory() != null) {
            messages.addAll(context.conversationHistory());
        }

        messages.add(ChatMessage.user(intent.rawMessage() != null ? intent.rawMessage() : "Analyze property image"));

        List<ToolDefinition> toolDefinitions = buildToolDefinitions();
        List<String> executedTools = new ArrayList<>();
        Map<String, Object> metadataCollector = new HashMap<>();
        List<Map<String, Object>> executionSteps = new ArrayList<>();
        long agentStartTime = System.currentTimeMillis();
        UUID convId = context != null ? context.conversationId() : null;

        for (int turn = 1; turn <= MAX_AGENT_TURNS; turn++) {
            long turnStartTime = System.currentTimeMillis();
            if (activityPublisher != null && convId != null) {
                activityPublisher.publishTurnStart(convId, "VisionAgent", turn, MAX_AGENT_TURNS, messages.size(), toolDefinitions.size());
            }

            LlmToolCallResponse llmResponse = llmClient.chatWithTools(messages, toolDefinitions);

            if (llmResponse == null) {
                return AgentResponse.fallback("I encountered an issue analyzing your visual request. Please try again.");
            }

            long turnDuration = System.currentTimeMillis() - turnStartTime;
            if (activityPublisher != null && convId != null) {
                List<String> proposedTools = llmResponse.hasToolCalls()
                        ? llmResponse.toolCalls().stream().map(LlmToolCall::name).toList()
                        : List.of();
                activityPublisher.publishTurnEnd(convId, "VisionAgent", turn, MAX_AGENT_TURNS, turnDuration, proposedTools);
            }

            if (!llmResponse.hasToolCalls()) {
                long synthStart = System.currentTimeMillis();
                if (activityPublisher != null && convId != null) {
                    activityPublisher.publishSynthesisStart(convId, "VisionAgent");
                }
                String responseText = llmResponse.textResponse();

                List<LumenResponseBlock> backendBlocks = new ArrayList<>(
                        LumenResponseFormatter.extractBlocksFromMetadata(metadataCollector)
                );

                if (!executionSteps.isEmpty()) {
                    metadataCollector.put("executionPlan", executionSteps);
                }

                String formattedJson = LumenResponseFormatter.formatResponse(
                        responseText,
                        backendBlocks
                );

                // Hydrate blocks with authoritative property details and image URLs
                formattedJson = enrichVisionBlocks(formattedJson);

                String metadataJson = null;
                if (!metadataCollector.isEmpty()) {
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadataCollector);
                    } catch (Exception ignored) {}
                }

                if (activityPublisher != null && convId != null) {
                    long synthDuration = System.currentTimeMillis() - synthStart;
                    activityPublisher.publishSynthesisEnd(convId, "VisionAgent", synthDuration, "Generated visual search analysis & recommendations");
                    activityPublisher.publishCompleted(convId, "VisionAgent", System.currentTimeMillis() - agentStartTime, executedTools.size(), turn);
                }

                return AgentResponse.builder()
                        .text(formattedJson)
                        .toolCalls(List.copyOf(executedTools))
                        .metadataJson(metadataJson)
                        .build();
            }

            for (LlmToolCall toolCall : llmResponse.toolCalls()) {
                String toolName = toolCall.name();
                Tool<?, ?> tool = toolRegistry.get(toolName);

                if (tool == null) {
                    messages.add(ChatMessage.toolResponse(toolName, "{\"error\": \"Tool not available: " + toolName + "\"}"));
                    continue;
                }

                if (activityPublisher != null && convId != null) {
                    activityPublisher.publishToolStart(convId, "VisionAgent", toolName, toolCall.arguments(), turn);
                }
                long toolStart = System.currentTimeMillis();

                try {
                    Object rawArgs = toolCall.arguments();
                    if (expressionEngine != null && rawArgs != null) {
                        com.luna.aggarly.scheduler.workflow.ExecutionContext exprCtx =
                                com.luna.aggarly.scheduler.workflow.ExecutionContext.forTask(
                                        user != null ? user.getUserId() : null,
                                        null,
                                        null,
                                        "UTC"
                                );
                        if (convId != null) {
                            exprCtx.variables().put("conversationId", convId.toString());
                        }
                        rawArgs = expressionEngine.resolve(rawArgs, exprCtx);
                    }
                    Object typedParams = objectMapper.convertValue(rawArgs, tool.parameterType());
                    Set<ConstraintViolation<Object>> violations = validator.validate(typedParams);
                    if (!violations.isEmpty()) {
                        messages.add(ChatMessage.toolResponse(toolName, "{\"error\": \"Validation failed: " + violations.iterator().next().getMessage() + "\"}"));
                        if (activityPublisher != null && convId != null) {
                            activityPublisher.publishToolEnd(convId, "VisionAgent", toolName, System.currentTimeMillis() - toolStart, toolCall.arguments(), "Validation failed", false, turn);
                        }
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Tool<Object, Object> executableTool = (Tool<Object, Object>) tool;
                    ToolResult<Object> result = executableTool.execute(typedParams, user);
                    executedTools.add(toolName);

                    long duration = System.currentTimeMillis() - toolStart;
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "VisionAgent", toolName, duration, toolCall.arguments(), result != null ? result.getData() : null, result != null && result.isSuccess(), turn);
                    }

                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("stepNumber", executionSteps.size() + 1);
                    step.put("agentName", "VisionAgent");
                    step.put("toolName", toolName);
                    step.put("status", result != null && result.isSuccess() ? "COMPLETED" : "FAILED");
                    step.put("durationMs", duration);
                    step.put("input", toolCall.arguments());
                    step.put("summary", result != null && result.isSuccess() ? "Executed " + toolName + " successfully" : "Execution failed");
                    executionSteps.add(step);

                    if (result != null && result.isSuccess() && result.getData() != null) {
                        metadataCollector.put(toolName, result.getData());

                        if (toolName.equals("vision.search")) {
                            metadataCollector.put("cardType", "VISION_SEARCH");
                            metadataCollector.put("visionResults", result.getData());

                            if (result.getData() instanceof List<?> list) {
                                List<Map<String, Object>> propList = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof VisionSearchResult vsr) {
                                        Map<String, Object> pm = new LinkedHashMap<>();
                                        pm.put("id", vsr.propertyId() != null ? vsr.propertyId().toString() : "");
                                        pm.put("title", vsr.title());
                                        pm.put("location", (vsr.city() != null ? vsr.city() : "") + (vsr.country() != null ? ", " + vsr.country() : ""));
                                        pm.put("nightlyPrice", vsr.pricePerNight() != null ? vsr.pricePerNight() : 0.0);
                                        pm.put("rating", 4.95);
                                        pm.put("imageUrl", formatImageUrl(vsr.bestMatchImageUrl()));
                                        pm.put("coverImageUrl", formatImageUrl(vsr.bestMatchImageUrl()));
                                        pm.put("maxGuests", vsr.maxGuests());
                                        pm.put("features", vsr.matchedFeatures() != null ? vsr.matchedFeatures() : List.of());
                                        propList.add(pm);
                                    }
                                }
                                metadataCollector.put("properties", propList);
                            }
                        } else if (toolName.equals("vision.getPhotoTour")) {
                            metadataCollector.put("cardType", "PHOTO_TOUR");
                            metadataCollector.put("photoTour", result.getData());
                        } else if (toolName.equals("vision.compareProperties")) {
                            metadataCollector.put("cardType", "PROPERTY_COMPARE");
                            metadataCollector.put("compareData", result.getData());
                        } else if (toolName.equals("vision.getPropertyVisualProfile")) {
                            metadataCollector.put("visualProfile", result.getData());
                        }
                    }

                    String serializedResult = objectMapper.writeValueAsString(result != null ? result.getData() : "Success");
                    messages.add(ChatMessage.assistant(createToolCallMessage(toolCall)));
                    messages.add(ChatMessage.toolResponse(toolName, serializedResult));
                } catch (Exception ex) {
                    log.error("Tool '{}' execution failed", toolName, ex);
                    long duration = System.currentTimeMillis() - toolStart;
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "VisionAgent", toolName, duration, toolCall.arguments(), ex.getMessage(), false, turn);
                    }
                    messages.add(ChatMessage.toolResponse(toolName, "{\"error\": \"" + ex.getMessage() + "\"}"));
                }
            }
        }

        return AgentResponse.fallback("I completed the visual reasoning loop.");
    }

    /**
     * Hydrates property references inside vision blocks with authentic titles, cover photos, and prices.
     */
    private String enrichVisionBlocks(String json) {
        if (json == null || json.isBlank() || propertyService == null) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.has("blocks") || !root.get("blocks").isArray()) {
                return json;
            }

            boolean modified = false;

            for (JsonNode block : root.get("blocks")) {
                String type = block.has("type") ? block.get("type").asText() : "";
                if ("property".equals(type) && block.has("data")) {
                    JsonNode dataNode = block.get("data");
                    if (dataNode.has("id")) {
                        String propIdStr = dataNode.get("id").asText();
                        try {
                            UUID propId = UUID.fromString(propIdStr);
                            PropertyResponse p = propertyService.getPropertyById(propId);
                            if (p != null) {
                                Map<String, Object> map = objectMapper.convertValue(dataNode, Map.class);
                                List<String> rawImageKeys = p.images() != null
                                        ? p.images().stream().map(PropertyImageResponse::objectKey).toList()
                                        : List.of();
                                List<String> formattedImages = rawImageKeys.stream()
                                        .map(this::formatImageUrl)
                                        .toList();
                                String cover = (rawImageKeys != null && !rawImageKeys.isEmpty()) ? rawImageKeys.get(0) : null;
                                if (p.images() != null) {
                                    cover = p.images().stream()
                                            .filter(PropertyImageResponse::isCover)
                                            .findFirst()
                                            .map(PropertyImageResponse::objectKey)
                                            .orElse(cover);
                                }
                                String formattedCover = formatImageUrl(cover);
                                map.put("coverImageUrl", formattedCover);
                                map.put("imageUrl", formattedCover);
                                map.put("images", formattedImages);
                                map.put("imageUrls", formattedImages);

                                ((com.fasterxml.jackson.databind.node.ObjectNode) block).set("data", objectMapper.valueToTree(map));
                                modified = true;
                            }
                        } catch (Exception e) {
                            log.debug("Could not enrich property block in VisionAgent for id: {}", propIdStr, e);
                        }
                    }
                } else if ("property_list".equals(type) && block.has("data") && block.get("data").has("properties")) {
                    JsonNode propsNode = block.get("data").get("properties");
                    if (propsNode.isArray()) {
                        List<Map<String, Object>> enrichedList = new ArrayList<>();
                        for (JsonNode pNode : propsNode) {
                            Map<String, Object> pMap = objectMapper.convertValue(pNode, Map.class);
                            if (pNode.has("id")) {
                                String propIdStr = pNode.get("id").asText();
                                try {
                                    UUID propId = UUID.fromString(propIdStr);
                                    PropertyResponse p = propertyService.getPropertyById(propId);
                                    if (p != null) {
                                        List<String> rawImageKeys = p.images() != null
                                                ? p.images().stream().map(PropertyImageResponse::objectKey).toList()
                                                : List.of();
                                        List<String> formattedImages = rawImageKeys.stream()
                                                .map(this::formatImageUrl)
                                                .toList();
                                        String cover = (rawImageKeys != null && !rawImageKeys.isEmpty()) ? rawImageKeys.get(0) : null;
                                        if (p.images() != null) {
                                            cover = p.images().stream()
                                                    .filter(PropertyImageResponse::isCover)
                                                    .findFirst()
                                                    .map(PropertyImageResponse::objectKey)
                                                    .orElse(cover);
                                        }
                                        String formattedCover = formatImageUrl(cover);
                                        pMap.put("coverImageUrl", formattedCover);
                                        pMap.put("imageUrl", formattedCover);
                                        pMap.put("images", formattedImages);
                                    }
                                } catch (Exception e) {
                                    log.debug("Could not enrich property summary item for id: {}", propIdStr, e);
                                }
                            }
                            enrichedList.add(pMap);
                        }
                        ((com.fasterxml.jackson.databind.node.ObjectNode) block.get("data")).set("properties", objectMapper.valueToTree(enrichedList));
                        modified = true;
                    }
                }
            }

            return modified ? objectMapper.writeValueAsString(root) : json;
        } catch (Exception e) {
            log.warn("Failed to enrich vision blocks", e);
            return json;
        }
    }

    private String formatImageUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;
        return "http://localhost:8081/api/v1/storage/files/view?key=" + key;
    }

    private List<ToolDefinition> buildToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool<?, ?> tool : toolRegistry.values()) {
            Map<String, Object> paramSchema = tool.parameterSchema() != null
                    ? objectMapper.convertValue(tool.parameterSchema(), Map.class)
                    : Map.of("type", "object", "properties", Map.of());

            Map<String, Object> respSchema = tool.responseSchema() != null
                    ? objectMapper.convertValue(tool.responseSchema(), Map.class)
                    : Map.of();

            definitions.add(new ToolDefinition(
                    tool.name(),
                    tool.description(),
                    paramSchema,
                    respSchema
            ));
        }
        return definitions;
    }

    private String createToolCallMessage(LlmToolCall toolCall) {
        try {
            return objectMapper.writeValueAsString(Map.of("tool_calls", List.of(toolCall)));
        } catch (Exception ex) {
            return "{\"tool_calls\": [{\"name\": \"" + toolCall.name() + "\"}]}";
        }
    }
}
