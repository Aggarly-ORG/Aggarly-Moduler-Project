package com.luna.aggarly.aiagent.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.model.LumenResponseBlock;
import com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter;
import com.luna.aggarly.aiagent.engine.records.*;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.schedule.record.CreateScheduleResponse;
import com.luna.aggarly.common.security.SecurityUtils;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class SchedulingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(SchedulingAgent.class);
    private static final int MAX_AGENT_TURNS = 5;

    private static final String SCHEDULING_AGENT_SYSTEM_PROMPT = """
        You are Aggarly's AI Scheduling & Workflow Automation Agent.

        You are specialized in compiling natural language automation requests from users,
        hosts, and administrators into validated, persistent, and deterministic execution plans.

        ================================================================
        CORE PRINCIPLE: AI PLANS ONCE, BACKEND EXECUTES DETERMINISTICALLY
        ================================================================

        When a user asks for a recurring schedule, automated report, or event-driven alert,
        you do NOT execute the end domain services directly.
        Instead, you invoke the tool "schedule.create" with a structured plan.

        ================================================================
        AVAILABLE BACKEND OPERATIONS (for "service" in workflow steps)
        ================================================================
        - "host.earningsReport": Calculates host revenue for a period. Params: {"currency": "USD"}.
        - "host.upcomingReservations": Fetches upcoming check-ins/reservations for the host.
        - "notification.send": Sends an in-app push alert. Params: {"userId": "<id>", "title": "<t>", "message": "<m>"}.
        - "notification.sendEmail": Sends a templated email. Params: {"userId": "<id>", "template": "<NAME>", "data": <object>}.
        - "notification.sendBookingNotification": Sends instant alert for booking events. Params: {"hostId": "<id>", "bookingId": "<id>", "propertyId": "<id>"}.
        - "booking.getById": Fetches booking info. Params: {"bookingId": "<id>"}.
        - "property.getById": Fetches property details. Params: {"propertyId": "<id>"}.

        ================================================================
        AVAILABLE RUNTIME FUNCTIONS & DYNAMIC EXPRESSIONS
        ================================================================
        - "{{obj.<path>}}": Full dot-path access into the entire execution configuration, trigger, and context:
            * "{{obj.trigger.event.value}}": Event value from the trigger config
            * "{{obj.trigger.config.<field>}}": Specific trigger configuration properties
            * "{{obj.event.<fieldName>}}": Payload fields from an incoming domain event
            * "{{obj.step.<stepId>.result}}": The output of a preceding step in the plan
            * "{{obj.task.id}}", "{{obj.userId}}", "{{obj.timezone}}": Execution metadata
        - "{{function.SecurityUtils.getCurrentUserId()}}": The task owner's UUID.
        - "{{function.DateUtils.today()}}": Current date YYYY-MM-DD.
        - "{{function.DateUtils.previousMonthStart()}}": First day of previous month.
        - "{{function.DateUtils.previousMonthEnd()}}": Last day of previous month.
        - "{{step.<stepId>.result}}": Shortcut for step result output.
        - "{{event.<fieldName>}}": Shortcut for incoming domain event payload.

        ================================================================
        TRIGGER TYPES & CONFIGURATION EXAMPLES
        ================================================================

        1. MONTHLY TRIGGER (e.g. "Send me my earnings on the 10th of every month at 9 AM"):
           triggerType: "MONTHLY"
           triggerConfig: {"dayOfMonth": 10, "time": "09:00"}
           plan: {
             "steps": [
               {
                 "id": "get_earnings",
                 "type": "service_call",
                 "service": "host.earningsReport",
                 "arguments": {"currency": "USD"}
               },
               {
                 "id": "send_report",
                 "type": "service_call",
                 "service": "notification.sendEmail",
                 "arguments": {
                   "userId": "{{function.SecurityUtils.getCurrentUserId()}}",
                   "template": "HOST_EARNINGS_REPORT",
                   "data": "{{step.get_earnings.result}}"
                 }
               }
             ]
           }

        2. WEEKLY TRIGGER (e.g. "Every Monday at 9 AM send my upcoming reservations"):
           triggerType: "WEEKLY"
           triggerConfig: {"days": ["MONDAY"], "time": "09:00"}
           plan: {
             "steps": [
               {
                 "id": "reservations",
                 "type": "service_call",
                 "service": "host.upcomingReservations",
                 "arguments": {}
               },
               {
                 "id": "send_reservations",
                 "type": "service_call",
                 "service": "notification.sendEmail",
                 "arguments": {
                   "userId": "{{function.SecurityUtils.getCurrentUserId()}}",
                   "template": "UPCOMING_RESERVATIONS",
                   "data": "{{step.reservations.result}}"
                 }
               }
             ]
           }

        3. EVENT TRIGGER (e.g. "Whenever someone books my property, notify me"):
           triggerType: "EVENT"
           triggerConfig: {"event": "BOOKING.CREATED"}
           plan: {
             "steps": [
               {
                 "id": "notify",
                 "type": "service_call",
                 "service": "notification.sendBookingNotification",
                 "arguments": {
                   "hostId": "{{event.hostId}}",
                   "bookingId": "{{event.bookingId}}",
                   "propertyId": "{{event.propertyId}}"
                 }
               }
             ]
           }

        4. INTERVAL TRIGGER (e.g. "Every 3 days"):
           triggerType: "INTERVAL"
           triggerConfig: {"every": 3, "unit": "DAYS"}

        ================================================================
        PRESENTATION CONTRACT & FRONTEND ROUTES
        ================================================================
        Always return clean valid JSON matching the Lumen Presentation Contract:
        {
          "version": "1",
          "blocks": [
            { "type": "text", "content": "..." },
            { "type": "actions", "items": [...] }
          ]
        }

        - "html": Use when the user requests an interactive widget, custom card, or web view.
          * Interactive HTML Forms & Components: When generating forms or buttons in "data.html", standard `<form>` submissions automatically relay their submitted fields directly into the chat for Lumen to process!
          * You can also call `AggarlyBridge.sendMessage('...')` or `AggarlyBridge.navigate('/properties/<id>')` in your JavaScript / `onclick` / `onsubmit`.

        Our frontend web application runs at http://localhost:3000 and has the following official routes:
        - Property Details: http://localhost:3000/properties/{propertyId}
        - Conversation View: http://localhost:3000/conversations/view/{conversationId}
        - Concierge Dashboard: http://localhost:3000/
        - Auth Portal: http://localhost:3000/oauth2/callback
        """;

    private static final Set<String> SUPPORTED_TOOLS = Set.of(
            "schedule.create",
            "schedule.list",
            "schedule.pause",
            "schedule.resume",
            "schedule.runNow",
            "schedule.cancel"
    );

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Map<String, Tool<?, ?>> toolRegistry = new HashMap<>();

    @Autowired
    public SchedulingAgent(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            Validator validator,
            List<Tool<?, ?>> tools
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.validator = validator;

        if (tools != null) {
            for (Tool<?, ?> tool : tools) {
                if (SUPPORTED_TOOLS.contains(tool.name())) {
                    toolRegistry.put(tool.name(), tool);
                }
            }
        }
        log.info("SchedulingAgent initialized with {} tools: {}", toolRegistry.size(), toolRegistry.keySet());
    }

    @Override
    public String name() {
        return "SchedulingAgent";
    }

    @Override
    public String description() {
        return "Compiles natural language scheduling requests into persistent, validated automated workflows.";
    }

    @Override
    public boolean supports(IntentCategory category) {
        return category == IntentCategory.SCHEDULE_AUTOMATION;
    }

    @Override
    public List<String> supportedTools() {
        return SUPPORTED_TOOLS.stream().toList();
    }

    @Override
    public AgentResponse handle(ClassifiedIntent intent, ConversationContext context, UserPrincipal ignoredUser) {
        UserPrincipal user = SecurityUtils.getCurrentUserPrincipal();
        log.info("SchedulingAgent started for user: {}", user != null ? user.getUserId() : "unauthenticated");

        List<ToolDefinition> toolDefinitions = buildToolDefinitions();
        List<ChatMessage> messages = buildConversation(intent, context);
        List<String> executedTools = new ArrayList<>();
        Map<String, Object> metadataCollector = new HashMap<>();

        for (int turn = 1; turn <= MAX_AGENT_TURNS; turn++) {
            log.debug("SchedulingAgent turn {}/{}", turn, MAX_AGENT_TURNS);

            LlmToolCallResponse llmResponse;
            try {
                llmResponse = llmClient.chatWithTools(messages, toolDefinitions);
            } catch (Exception ex) {
                log.error("SchedulingAgent: LLM error on turn {}", turn, ex);
                return failureResponse("I encountered an issue setting up your automated schedule. Please try again.", executedTools);
            }

            if (!llmResponse.hasToolCalls()) {
                String text = llmResponse.textResponse();
                if (text == null || text.isBlank()) {
                    return failureResponse("I was unable to schedule your task.", executedTools);
                }
                String formatted = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.formatResponse(
                        text,
                        com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.extractBlocksFromMetadata(metadataCollector)
                );
                return AgentResponse.builder()
                        .text(formatted)
                        .toolCalls(List.copyOf(executedTools))
                        .metadataJson(serializeMetadata(metadataCollector))
                        .build();
            }

            for (LlmToolCall toolCall : llmResponse.toolCalls()) {
                String toolName = toolCall.name();
                Tool<?, ?> tool = toolRegistry.get(toolName);

                if (tool == null) {
                    messages.add(ChatMessage.toolResponse(toolName, "{\"success\":false,\"error\":\"UNKNOWN_TOOL\"}"));
                    continue;
                }

                try {
                    Object typedParams = objectMapper.convertValue(toolCall.arguments(), tool.parameterType());
                    @SuppressWarnings("unchecked")
                    Tool<Object, Object> executableTool = (Tool<Object, Object>) tool;
                    ToolResult<Object> result = executableTool.execute(typedParams, user);
                    executedTools.add(toolName);

                    if (result.isSuccess() && result.getData() != null) {
                        metadataCollector.put("lastScheduleAction", result.getData());
                    }

                    messages.add(ChatMessage.assistant(createToolCallMessage(toolCall)));
                    messages.add(ChatMessage.toolResponse(toolName, objectMapper.writeValueAsString(result)));
                } catch (Exception ex) {
                    log.error("Tool '{}' execution failed", toolName, ex);
                    messages.add(ChatMessage.toolResponse(toolName, "{\"success\":false,\"error\":\"" + ex.getMessage() + "\"}"));
                }
            }
        }

        return failureResponse("Scheduling workflow reached maximum turns.", executedTools);
    }

    private List<ToolDefinition> buildToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool<?, ?> tool : toolRegistry.values()) {
            Map<String, Object> paramSchema = tool.parameterSchema() != null
                    ? objectMapper.convertValue(tool.parameterSchema(), Map.class)
                    : Map.of("type", "object");
            Map<String, Object> respSchema = tool.responseSchema() != null
                    ? objectMapper.convertValue(tool.responseSchema(), Map.class)
                    : Map.of();
            definitions.add(new ToolDefinition(tool.name(), tool.description(), paramSchema, respSchema));
        }
        return definitions;
    }

    private List<ChatMessage> buildConversation(ClassifiedIntent intent, ConversationContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String memoryBlock = context != null ? context.formatUserMemoriesBlock() : "";
        String activeFilters = (context != null && context.activeSearchContext() != null && context.activeSearchContext().getFiltersJson() != null)
                ? "\n\nActive Search Context: " + context.activeSearchContext().getFiltersJson()
                : "";

        String dynamicSystemPrompt = SCHEDULING_AGENT_SYSTEM_PROMPT
                + "\n\nCurrent Date and Time: " + currentTime
                + memoryBlock
                + activeFilters;
        messages.add(ChatMessage.system(dynamicSystemPrompt));
        if (context != null && context.conversationHistory() != null) {
            messages.addAll(context.conversationHistory());
        }
        return messages;
    }

    private String createToolCallMessage(LlmToolCall toolCall) {
        try {
            return objectMapper.writeValueAsString(Map.of("tool_calls", List.of(toolCall)));
        } catch (Exception ignored) {
            return "{\"tool_calls\":[]}";
        }
    }

    private AgentResponse failureResponse(String message, List<String> executedTools) {
        String json = LumenResponseFormatter.formatResponse(
                null,
                List.of(LumenResponseBlock.error("SCHEDULE_ERROR", message))
        );
        return AgentResponse.builder()
                .text(json)
                .toolCalls(List.copyOf(executedTools))
                .build();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
