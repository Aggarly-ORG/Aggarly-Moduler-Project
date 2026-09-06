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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class VisionAgent implements Agent {

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
              "content": "\"An evocative opening quote summarizing the visual aesthetic.\"\n\nHelpful concierge narrative explaining the visual findings."
            }
          ]
        }

        ================================================================
        RUNTIME EXPRESSION INJECTION & DYNAMIC FUNCTIONS
        ================================================================
        When invoking tools, you have access to Aggarly's Runtime Expression Engine.
        All tool arguments are dynamically pre-evaluated through the expression engine before execution!
        You can use:
        - Root Object & Context: {{obj.<field>}}, {{user.id}}, {{current_conversation()}}, {{origin_channel()}}
        - Date Functions: {{today()}}, {{now()}}, {{date_add(today(), 3, 'DAYS')}}, {{date_sub(today(), 1, 'MONTHS')}}, {{format_date(today(), 'yyyy-MM-dd')}}
        - Entity Lookups: {{property('<id>').title}}, {{booking('<id>').status}}, {{user('<id>').email}}
        - String & Math Utilities: {{upper(str)}}, {{format_currency(amount, 'USD')}}, {{join(list, ', ')}}, {{first(list)}}, {{coalesce(a, b)}}, {{ternary(cond, trueVal, falseVal)}}
        """;

    private final LlmClient llmClient;
    private final ConfirmationGate confirmationGate;
    private final ObjectMapper objectMapper;
    private final Validator validator;
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
            @Autowired(required = false) com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher,
            @Autowired(required = false) com.luna.aggarly.common.expression.ExpressionEngine expressionEngine,
            @Qualifier("toolRegistry") Map<String, Tool<?, ?>> sharedToolRegistry
    ) {
        this.llmClient = llmClient;
        this.confirmationGate = confirmationGate;
        this.objectMapper = objectMapper;
        this.validator = validator;
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

                List<com.luna.aggarly.aiagent.engine.model.LumenResponseBlock> backendBlocks = new ArrayList<>(
                        LumenResponseFormatter.extractBlocksFromMetadata(metadataCollector)
                );

                if (!executionSteps.isEmpty()) {
                    Map<String, Object> planData = new LinkedHashMap<>();
                    planData.put("title", "Visual Search & Inspection Plan");
                    planData.put("totalSteps", executionSteps.size());
                    planData.put("totalDurationMs", System.currentTimeMillis() - agentStartTime);
                    planData.put("steps", executionSteps);
                    backendBlocks.add(0, com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.executionPlan(planData));
                    metadataCollector.put("executionPlan", executionSteps);
                }

                String formattedJson = LumenResponseFormatter.formatResponse(
                        responseText,
                        backendBlocks
                );
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
