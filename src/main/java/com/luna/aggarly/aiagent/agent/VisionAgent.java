package com.luna.aggarly.aiagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.ConfirmationGate;
import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
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
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class VisionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(VisionAgent.class);
    private static final int MAX_AGENT_TURNS = 5;

    private static final String VISION_AGENT_SYSTEM_PROMPT = """
        You are Aggarly's Multimodal Vision & Aesthetic Concierge AI Assistant.

        You specialize in visual property discovery, image-based search, style matching, room coverage inspection, and photo aesthetic comparison.

        ================================================================
        CORE PRINCIPLE & SOURCE OF TRUTH
        ================================================================
        The backend vision tools are the authoritative source of truth for:
        - Visual search and image similarity matches
        - Room coverage and scene classification
        - Verified visual amenities (pools, fireplaces, bathtubs, balconies, sea views)
        - Perceptual quality scores and style attributes

        NEVER invent, guess, or assume visual features. Use the tools provided.

        ================================================================
        LUMEN AI — AGENT RESPONSE PRESENTATION FORMAT
        ================================================================
        Return your final response as strict JSON adhering to the Lumen Presentation Contract:
        {
          "version": "1",
          "blocks": [
            {
              "type": "text",
              "content": "\\"An evocative opening quote summarizing the visual aesthetic.\\"\\n\\nHelpful concierge narrative explaining the visual findings."
            }
          ]
        }
        """;

    private final LlmClient llmClient;
    private final ConfirmationGate confirmationGate;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Map<String, Tool<?, ?>> toolRegistry = new HashMap<>();

    public static final Set<String> SUPPORTED_TOOLS = Set.of(
            "vision.searchByText",
            "vision.searchByImage",
            "vision.searchMultimodal",
            "vision.compareProperties",
            "vision.getPropertyVisualProfile",
            "vision.getImageMetadata",
            "vision.extractVisualPreferences",
            "vision.analyzeReferenceImage",
            "vision.searchSimilarToProperty"
    );

    @Autowired
    public VisionAgent(
            LlmClient llmClient,
            ConfirmationGate confirmationGate,
            ObjectMapper objectMapper,
            Validator validator,
            List<Tool<?, ?>> tools
    ) {
        this.llmClient = llmClient;
        this.confirmationGate = confirmationGate;
        this.objectMapper = objectMapper;
        this.validator = validator;

        if (tools != null) {
            for (Tool<?, ?> tool : tools) {
                if (SUPPORTED_TOOLS.contains(tool.name())) {
                    this.toolRegistry.put(tool.name(), tool);
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

        for (int turn = 0; turn < MAX_AGENT_TURNS; turn++) {
            LlmToolCallResponse llmResponse = llmClient.chatWithTools(messages, toolDefinitions);

            if (llmResponse == null) {
                return AgentResponse.fallback("I encountered an issue analyzing your visual request. Please try again.");
            }

            if (!llmResponse.hasToolCalls()) {
                String responseText = llmResponse.textResponse();
                String formattedJson = LumenResponseFormatter.formatResponse(
                        responseText,
                        LumenResponseFormatter.extractBlocksFromMetadata(metadataCollector)
                );
                String metadataJson = null;
                if (!metadataCollector.isEmpty()) {
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadataCollector);
                    } catch (Exception ignored) {}
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

                try {
                    Object typedParams = objectMapper.convertValue(toolCall.arguments(), tool.parameterType());
                    Set<ConstraintViolation<Object>> violations = validator.validate(typedParams);
                    if (!violations.isEmpty()) {
                        messages.add(ChatMessage.toolResponse(toolName, "{\"error\": \"Validation failed: " + violations.iterator().next().getMessage() + "\"}"));
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Tool<Object, Object> executableTool = (Tool<Object, Object>) tool;
                    ToolResult<Object> result = executableTool.execute(typedParams, user);
                    executedTools.add(toolName);

                    if (result != null && result.isSuccess() && result.getData() != null) {
                        metadataCollector.put(toolName, result.getData());
                    }

                    String serializedResult = objectMapper.writeValueAsString(result != null ? result.getData() : "Success");
                    messages.add(ChatMessage.assistant(createToolCallMessage(toolCall)));
                    messages.add(ChatMessage.toolResponse(toolName, serializedResult));
                } catch (Exception ex) {
                    log.error("Tool '{}' execution failed", toolName, ex);
                    messages.add(ChatMessage.toolResponse(toolName, "{\"error\": \"" + ex.getMessage() + "\"}"));
                }
            }
        }

        return AgentResponse.fallback("I completed the visual reasoning loop.");
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
