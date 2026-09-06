package com.luna.aggarly.aiagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.engine.AgentResponse;
import com.luna.aggarly.aiagent.engine.LlmClient;
import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.records.*;
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
public class TravelAgent implements Agent {

    private static final int MAX_AGENT_TURNS = 6;

    private static final String TRAVEL_AGENT_SYSTEM_PROMPT = """
        You are Aggarly's Travel Concierge AI Assistant.

        You are an autonomous assistant specialized ONLY in helping
        guests plan and enjoy their stay by providing local travel
        information: weather forecasts, attractions, dining, transportation,
        and upcoming events near their accommodation.

        ================================================================
        CORE PRINCIPLE
        ================================================================

        You are a decision-making layer, NOT the source of truth.

        The backend tools are the authoritative source for:
        - weather forecasts and conditions at a destination
        - tourist attractions, museums, and cultural sights
        - restaurants and dining recommendations
        - transportation options and transit guidance
        - local events, concerts, and festivals

        NEVER invent, assume, or fabricate any of the above.

        If a tool provides data, present that data.

        If the location is missing or ambiguous, ask the user to clarify it
        before calling any tool.

        ================================================================
        TOOL USAGE
        ================================================================

        When a tool is required, return ONLY a valid JSON tool call:

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

        Tool names and parameter names are case-sensitive.

        NEVER invent a tool name.
        NEVER invent a parameter.
        NEVER omit a parameter listed in the "required" array.
        NEVER put explanatory text inside a tool argument value.

        ================================================================
        TOOL SELECTION — REQUIRED PARAMETER RULE
        ================================================================

        Before calling a tool, read its JSON schema "required" array carefully.

        Example Tool Schema:
        {
          "type": "object",
          "properties": {
            "arg1": { "type": "string", "description": "..." },
            "arg2": { "type": "string", "description": "..." }
          },
          "required": [ "arg1" ]
        }

        Rule: If arg1 AND arg2 are both missing, ask ONLY for arg1 (required).
        Do NOT ask for arg2 (optional). Once arg1 is present, call the tool.

        Do not fabricate location names, weather data, or event details.

        ================================================================
        WEATHER FORECAST
        ================================================================

        When the user asks about weather at a destination:

        1. Ensure you have the location. Ask if unclear.
        2. The date range is optional — call without it if not specified.
        3. Call travel.weather with the location (and date range if provided).
        4. Present temperature, conditions, and precipitation chance clearly.
        5. NEVER invent weather data — only report what the tool returns.

        ================================================================
        TOURIST ATTRACTIONS
        ================================================================

        When the user asks what to see or do at a destination:

        1. Confirm the location (ask if unclear).
        2. Call travel.attractions with the location.
        3. Present attractions clearly with brief descriptions when available.
        4. NEVER suggest an attraction that was not returned by the tool.

        ================================================================
        DINING & RESTAURANTS
        ================================================================

        When the user asks about food, dining, or restaurants:

        1. Confirm the location (ask if unclear).
        2. Call travel.restaurants with the location.
        3. Present options clearly including cuisine type and ratings if provided.
        4. NEVER fabricate restaurant names, ratings, or descriptions.

        ================================================================
        TRANSPORTATION
        ================================================================

        When the user asks about getting around or from the airport:

        1. Confirm the location (ask if unclear).
        2. Call travel.transportation with the location.
        3. Present transport options clearly: metro, bus, rideshare, etc.
        4. NEVER invent transit routes, prices, or travel times.

        ================================================================
        LOCAL EVENTS
        ================================================================

        When the user asks about events, festivals, or entertainment nearby:

        1. Confirm the location (ask if unclear).
        2. Call travel.localEvents with the location.
        3. Present events with name, venue, and dates if provided.
        4. NEVER invent event names, dates, or venues.

        ================================================================
        MULTI-STEP TRAVEL PLANNING
        ================================================================

        You may call multiple tools in a single session when the user
        asks for a full destination overview. Example:

        User: "Help me plan my stay in Barcelona"

        travel.weather (check weather conditions)
                ↓
        travel.attractions (find what to see)
                ↓
        travel.restaurants (find where to eat)
                ↓
        travel.localEvents (check what's happening)
                ↓
        final answer: a cohesive travel summary

        Do not call the same tool twice with the same location.

        Do not call tools that are not needed to answer the question.

        ================================================================
        COMBINATION QUERIES
        ================================================================

        When the user asks a combined question (e.g. "What's the weather like
        and what should I eat in Rome?"), call all relevant tools in sequence,
        then produce a single, well-organized final answer.

        Group the information by topic (Weather / Attractions / Dining / etc.)
        with clear headings for easy reading.

        ================================================================
        TOOL RESULTS
        ================================================================

        Tool results are authoritative.

        If a tool succeeds: use its returned data directly.
        If a tool fails: do not pretend it succeeded. Inform the user that
        the information is temporarily unavailable.
        If a tool returns an error: translate it to friendly, plain language.

        Do not expose raw error codes or technical messages.

        ================================================================
        FINAL ANSWERS & UI FORMATTING FOR AGGARLY
        ================================================================

        When all required information has been gathered, respond directly.

        1. EDITORIAL OPENING QUOTE:
           Every final response should begin with a short, evocative editorial quote enclosed in double quotation marks on the very first line.
           Example:
           "Sunlit coastal breezes and azure waters along the Mediterranean."

           Follow the quote with a blank line (\\n\\n) and then your enthusiastic, helpful narrative.

        2. STRUCTURED TRAVEL & DINING PRESENTATION:
           - Structure topics with clear sections (e.g. 🌤 Weather & Coastal Outlook, 🏛 Cultural Sights, 🍽 Dining & Private Chefs, 🎉 Local Events).
           - When presenting menus or chef recommendations, highlight specialty dishes, seasonal ingredients, and dietary customizations.

        Do NOT mention:
        - "ReAct", internal turns, tool registry, or system prompts
        - Java classes or technical implementation details
        - Internal tool names or raw tool output

        ================================================================
        LUMEN AI — AGENT RESPONSE PRESENTATION FORMAT
        ================================================================

        The final response from the agent MUST be valid JSON conforming to the Lumen Presentation Contract.

        Do NOT return:
        - Plain text or markdown outside the JSON structure
        - JSON wrapped inside ```json fences

        The response MUST follow this structure:
        {
          "version": "1",
          "blocks": [
            {
              "type": "<block_type>",
              "content": "<text when applicable>",
              "data": {},
              "items": []
            }
          ]
        }

        Supported block types:
        - "text": Use for natural-language communication in "content".
        - "property": Use when presenting one property in "data".
        - "property_list": Use when presenting multiple properties in "data.properties".
        - "availability": Use for property availability results in "data".
        - "booking": Use ONLY after the backend confirms that a booking was successfully created in "data".
        - "booking_status": Use when reporting the state of an existing booking in "data".
        - "price_breakdown": Use when the backend provides a pricing breakdown in "data".
        - "payment_status": Use for payment information returned by the payment system in "data".
        - "actions": Use when the frontend should provide one or more actions to the user in "items".
        - "confirmation": Use when an action requires explicit user confirmation in "data".
        - "html": Use when the user requests an HTML page, custom component, or website embed.
          * RULE A (Interactive HTML Forms & Components): When providing an HTML block with interactive forms, trip planners, activity selectors, or action buttons, make them fully functional!
            - Standard `<form>` submissions automatically relay their submitted fields directly into the chat for Lumen to process!
            - You can also add `data-title="..."` or `data-prompt="..."` to `<form>` or `<button>`.
            - You can also call `AggarlyBridge.sendMessage('...')` or `AggarlyBridge.navigate('/properties/<id>')` in your JavaScript / `onclick` / `onsubmit`.
            - Example Interactive Form in "data.html":
              "<!DOCTYPE html><html><head><style>body{font-family:'Segoe UI',sans-serif;background:#0f172a;color:#fff;padding:20px;margin:0;} .card{background:#1e293b;padding:20px;border-radius:16px;border:1px solid #334155;} select,input{width:100%;padding:10px;margin:8px 0;background:#090d16;border:1px solid #475569;border-radius:8px;color:#fff;} button{width:100%;padding:12px;background:#c04a26;border:none;border-radius:10px;color:#fff;font-weight:bold;cursor:pointer;margin-top:10px;}</style></head><body><div class='card'><h3>Customize Trip Itinerary</h3><form onsubmit='AggarlyBridge.sendMessage(\"Plan a \" + document.getElementById(\"style\").value + \" trip to \" + document.getElementById(\"city\").value); return false;'><input id='city' placeholder='City or Destination' required/><select id='style'><option value='romantic'>Romantic & Scenic</option><option value='family'>Family Friendly</option><option value='culinary'>Culinary & Wine</option></select><button type='submit'>Generate Custom Plan</button></form></div></body></html>"
          * RULE B (Website / URL Embed): When the user asks to embed, view, or open an external website, link, or help URL, provide the URL in "data.src":
            {"title": "Destination Guide", "badge": "Web Page", "src": "https://en.wikipedia.org/wiki/Travel", "height": 420}

        ================================================================
        INTERACTIVE ACTIONS & ACTION RECOMMENDATIONS
        ================================================================

        When suggesting interactive actions in an "actions" block ("items" array):
        - For property or place actions:
          {
            "label": "Open Property Page",
            "action": "property.open_page",
            "parameters": {
              "propertyId": "bba28d06-d53a-4c2c-9f32-20cb26468f00",
              "url": "http://localhost:3000/properties/bba28d06-d53a-4c2c-9f32-20cb26468f00"
            }
          }

          {
            "label": "Explore Local Dining",
            "action": "travel.explorePlaces",
            "parameters": { "category": "RESTAURANT" }
          }

        - For actions requiring user input (e.g. messaging host, custom budget, travel preferences):
          Set "requiresInput": true and provide an "inputs" array with field specifications:
          {
            "label": "Message Host",
            "action": "chat.message_host",
            "requiresInput": true,
            "parameters": { "propertyId": "bba28d06-d53a-4c2c-9f32-20cb26468f00" },
            "inputs": [
              {
                "name": "message",
                "type": "textarea",
                "label": "Your inquiry to the host",
                "placeholder": "e.g. Can you recommend local wine tours or private chefs?",
                "required": true
              }
            ]
          }

        ================================================================
        PURPOSEFUL HTML USAGE (THINK VISUALLY WHEN USEFUL)
        ================================================================

        The "html" block type allows rendering self-contained custom HTML/CSS widgets or embedded pages.
        Use it smartly and purposefully where visual design adds genuine value:

        - GOOD USE CASES for HTML:
          * Visual travel itineraries and day-by-day tour roadmaps
          * Multi-place comparison tables or interactive neighborhood maps
          * Website embeds when the user requests travel guides or external resources

        - DO NOT OVERUSE HTML:
          * Do NOT generate repetitive or useless HTML blocks on simple questions or everyday chat.
          * Use standard blocks ("property", "property_list", "actions") whenever possible.

        ================================================================
        FRONTEND APPLICATION ROUTES & HTML EMBEDS
        ================================================================

        Our frontend web application runs at http://localhost:3000 and has the following official routes:

        1. Property Details Page:
           - Route URL: http://localhost:3000/properties/{propertyId}
           - Purpose: Complete property listing page with full gallery, interactive calendar, amenities, host profile, pricing, and guest reviews.
           - To EMBED this page directly in chat via an "html" block:
             {
               "type": "html",
               "data": {
                 "title": "Property Details Page",
                 "badge": "Property Page",
                 "src": "http://localhost:3000/properties/bba28d06-d53a-4c2c-9f32-20cb26468f00",
                 "height": 550
               }
             }

        2. Conversation & Thread View:
           - Route URL: http://localhost:3000/conversations/view/{conversationId}
           - Purpose: Dedicated view for a specific chat thread, itinerary conversation, or concierge discussion.
           - To EMBED or link to this thread:
             {
               "type": "html",
               "data": {
                 "title": "Conversation View",
                 "badge": "Chat Thread",
                 "src": "http://localhost:3000/conversations/view/e5b19f33-0e33-42a3-8154-f55262b48467",
                 "height": 480
               }
             }

        3. Home & Concierge Dashboard:
           - Route URL: http://localhost:3000/
           - Purpose: Aggarly homepage and AI concierge search interface.

        4. Authentication Gateway:
           - Route URL: http://localhost:3000/oauth2/callback
           - Purpose: Login and OAuth2 callback authentication.

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

        ================================================================
        FAILURE HANDLING
        ================================================================

        If you cannot safely answer the request:

        - Do not guess or fabricate data
        - Do not call the same tool repeatedly with the same parameters
        - Ask for the missing location or clarification, then proceed
        - If all tools fail, apologize and suggest the user check a
          local travel guide or tourist information website
        """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher;
    private final com.luna.aggarly.common.expression.ExpressionEngine expressionEngine;

    private final Map<String, Tool<?, ?>> toolRegistry = new HashMap<>();

    private static final Set<String> SUPPORTED_TOOLS = Set.of(
            "travel.weather",
            "travel.attractions",
            "travel.restaurants",
            "travel.transportation",
            "travel.localEvents"
    );

    @Autowired
    public TravelAgent(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            Validator validator,
            @Autowired(required = false) com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher activityPublisher,
            @Autowired(required = false) com.luna.aggarly.common.expression.ExpressionEngine expressionEngine,
            @Qualifier("toolRegistry") Map<String, Tool<?, ?>> sharedToolRegistry
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.activityPublisher = activityPublisher;
        this.expressionEngine = expressionEngine;

        if (sharedToolRegistry != null) {
            for (String toolName : SUPPORTED_TOOLS) {
                Tool<?, ?> tool = sharedToolRegistry.get(toolName);
                if (tool != null) {
                    toolRegistry.put(toolName, tool);
                } else {
                    log.warn("TravelAgent: declared tool '{}' has no registered implementation", toolName);
                }
            }
        }

        log.info("TravelAgent initialized with {} registered tools: {}", toolRegistry.size(), toolRegistry.keySet());
    }

    @Override
    public String name() {
        return "TravelAgent";
    }

    @Override
    public String description() {
        return "Specialized in destination travel planning: local weather forecasts, tourist attractions, restaurant/dining spots, transit routes, and local cultural events.";
    }

    @Override
    public boolean supports(IntentCategory category) {
        return category == IntentCategory.TRAVEL_PLANNING;
    }

    @Override
    public List<String> supportedTools() {
        return SUPPORTED_TOOLS.stream().toList();
    }

    @Override
    public AgentResponse handle(ClassifiedIntent intent, ConversationContext context, UserPrincipal ignoredUser) {
        UserPrincipal user = SecurityUtils.getCurrentUserPrincipal();

        log.info("TravelAgent started: category={}, user={}",
                intent.category(), user != null ? user.getUserId() : "unauthenticated");

        List<ToolDefinition> toolDefinitions = buildToolDefinitions();
        List<ChatMessage> messages = buildConversation(intent, context);
        List<String> executedTools = new ArrayList<>();
        Map<String, Object> metadataCollector = new HashMap<>();
        List<Map<String, Object>> executionSteps = new ArrayList<>();
        long agentStartTime = System.currentTimeMillis();
        UUID convId = context != null ? context.conversationId() : null;

        for (int turn = 1; turn <= MAX_AGENT_TURNS; turn++) {
            log.debug("TravelAgent turn {}/{}", turn, MAX_AGENT_TURNS);
            long turnStartTime = System.currentTimeMillis();
            if (activityPublisher != null && convId != null) {
                activityPublisher.publishTurnStart(convId, "TravelAgent", turn, MAX_AGENT_TURNS, messages.size(), toolDefinitions.size());
            }

            LlmToolCallResponse llmResponse;
            try {
                llmResponse = llmClient.chatWithTools(messages, toolDefinitions);
            } catch (Exception ex) {
                log.error("TravelAgent: LLM invocation failed on turn {}", turn, ex);
                return failureResponse("I'm having trouble reaching my travel data right now. Please try again shortly.", executedTools);
            }

            long turnDuration = System.currentTimeMillis() - turnStartTime;
            if (activityPublisher != null && convId != null) {
                List<String> proposedTools = llmResponse.hasToolCalls()
                        ? llmResponse.toolCalls().stream().map(LlmToolCall::name).toList()
                        : List.of();
                activityPublisher.publishTurnEnd(convId, "TravelAgent", turn, MAX_AGENT_TURNS, turnDuration, proposedTools);
            }

            if (!llmResponse.hasToolCalls()) {
                long synthStart = System.currentTimeMillis();
                if (activityPublisher != null && convId != null) {
                    activityPublisher.publishSynthesisStart(convId, "TravelAgent");
                }
                String text = llmResponse.textResponse();
                if (text == null || text.isBlank()) {
                    log.warn("TravelAgent: empty LLM response on turn {}", turn);
                    return failureResponse("I wasn't able to generate a travel response. Please try rephrasing.", executedTools);
                }

                List<com.luna.aggarly.aiagent.engine.model.LumenResponseBlock> backendBlocks = new ArrayList<>(
                        com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.extractBlocksFromMetadata(metadataCollector)
                );

                if (!executionSteps.isEmpty()) {
                    Map<String, Object> planData = new LinkedHashMap<>();
                    planData.put("title", "Destination & Itinerary Plan");
                    planData.put("totalSteps", executionSteps.size());
                    planData.put("totalDurationMs", System.currentTimeMillis() - agentStartTime);
                    planData.put("steps", executionSteps);
                    backendBlocks.add(0, com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.executionPlan(planData));
                    metadataCollector.put("executionPlan", executionSteps);
                }

                String metadataJson = null;
                if (!metadataCollector.isEmpty()) {
                    try {
                        metadataJson = objectMapper.writeValueAsString(metadataCollector);
                    } catch (Exception e) {
                        log.warn("Failed to serialize travel metadata", e);
                    }
                }
                String formattedJson = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.formatResponse(
                        text,
                        backendBlocks
                );
                if (activityPublisher != null && convId != null) {
                    long synthDuration = System.currentTimeMillis() - synthStart;
                    activityPublisher.publishSynthesisEnd(convId, "TravelAgent", synthDuration, "Generated travel guide, itineraries & local insights");
                    activityPublisher.publishCompleted(convId, "TravelAgent", System.currentTimeMillis() - agentStartTime, executedTools.size(), turn);
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
                    log.warn("TravelAgent: LLM requested unknown tool '{}'", toolName);
                    messages.add(ChatMessage.toolResponse(toolName, buildErrorJson("UNKNOWN_TOOL",
                            "The tool '" + toolName + "' is not available to the Travel Agent.")));
                    continue;
                }

                if (activityPublisher != null && convId != null) {
                    activityPublisher.publishToolStart(convId, "TravelAgent", toolName, toolCall.arguments(), turn);
                }
                long toolStart = System.currentTimeMillis();

                if (tool.requiresAuthentication() && user == null) {
                    log.warn("TravelAgent: unauthenticated access attempted on tool '{}'", toolName);
                    messages.add(ChatMessage.toolResponse(toolName, buildErrorJson("AUTHENTICATION_REQUIRED",
                            "You must be signed in to access this travel feature.")));
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "TravelAgent", toolName, System.currentTimeMillis() - toolStart, toolCall.arguments(), "Authentication required", false, turn);
                    }
                    continue;
                }

                Object typedParameters;
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
                    typedParameters = objectMapper.convertValue(rawArgs, tool.parameterType());
                } catch (IllegalArgumentException ex) {
                    log.warn("TravelAgent: malformed arguments for tool '{}': {}", toolName, ex.getMessage());
                    messages.add(ChatMessage.toolResponse(toolName, buildErrorJson("INVALID_ARGUMENTS",
                            "The parameters provided for '" + toolName + "' are not valid.")));
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "TravelAgent", toolName, System.currentTimeMillis() - toolStart, toolCall.arguments(), "Invalid arguments", false, turn);
                    }
                    continue;
                }

                Set<ConstraintViolation<Object>> violations = validator.validate(typedParameters);
                if (!violations.isEmpty()) {
                    String detail = violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                            .sorted()
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("Invalid arguments.");
                    log.warn("TravelAgent: validation failed for '{}': {}", toolName, detail);
                    messages.add(ChatMessage.toolResponse(toolName, buildErrorJson("VALIDATION_FAILED", detail)));
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "TravelAgent", toolName, System.currentTimeMillis() - toolStart, toolCall.arguments(), detail, false, turn);
                    }
                    continue;
                }

                try {
                    log.info("TravelAgent: executing tool '{}' for user={}", toolName,
                            user != null ? user.getUserId() : "unauthenticated");

                    @SuppressWarnings("unchecked")
                    Tool<Object, Object> executableTool = (Tool<Object, Object>) tool;
                    ToolResult<Object> result = executableTool.execute(typedParameters, user);

                    executedTools.add(toolName);
                    long duration = System.currentTimeMillis() - toolStart;
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "TravelAgent", toolName, duration, toolCall.arguments(), result.getData(), result.isSuccess(), turn);
                    }

                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("stepNumber", executionSteps.size() + 1);
                    step.put("agentName", "TravelAgent");
                    step.put("toolName", toolName);
                    step.put("status", result.isSuccess() ? "COMPLETED" : "FAILED");
                    step.put("durationMs", duration);
                    step.put("input", toolCall.arguments());
                    step.put("summary", result.isSuccess() ? "Executed " + toolName + " successfully" : "Execution failed");
                    executionSteps.add(step);

                    if (result.isSuccess() && result.getData() != null) {
                        if (toolName.equals("travel.weather")) {
                            metadataCollector.put("cardType", "WEATHER_FORECAST");
                            metadataCollector.put("weatherData", result.getData());
                        } else if (toolName.equals("travel.restaurants")) {
                            metadataCollector.put("cardType", "PRIVATE_CHEF");
                            metadataCollector.put("chefData", result.getData());
                        }
                    }

                    messages.add(ChatMessage.assistant(buildToolCallJson(toolCall)));
                    messages.add(ChatMessage.toolResponse(toolName, serializeResult(result)));

                } catch (Exception ex) {
                    log.error("TravelAgent: tool '{}' threw an exception on turn {}", toolName, turn, ex);
                    long duration = System.currentTimeMillis() - toolStart;
                    if (activityPublisher != null && convId != null) {
                        activityPublisher.publishToolEnd(convId, "TravelAgent", toolName, duration, toolCall.arguments(), ex.getMessage(), false, turn);
                    }
                    messages.add(ChatMessage.toolResponse(toolName, buildErrorJson("TOOL_EXECUTION_FAILED",
                            "Travel data for '" + toolName + "' is temporarily unavailable. Please try again.")));
                }
            }
        }

        log.warn("TravelAgent: reached MAX_AGENT_TURNS ({}) without a final answer. tools={}", MAX_AGENT_TURNS, executedTools);
        return failureResponse("I wasn't able to complete your travel request. Please try rephrasing or contact our support team.", executedTools);
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
            definitions.add(new ToolDefinition(tool.name(), tool.description(), paramSchema, respSchema));
        }
        return definitions;
    }

    private List<ChatMessage> buildConversation(ClassifiedIntent intent, ConversationContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String memoryBlock = context != null ? context.formatUserMemoriesBlock() : "";
        String activeFilters = (context != null && context.activeSearchContext() != null && context.activeSearchContext().getFiltersJson() != null)
                ? "\n\nActive Search Context: " + context.activeSearchContext().getFiltersJson()
                : "";

        String dynamicSystemPrompt = TRAVEL_AGENT_SYSTEM_PROMPT
                + "\n\nCurrent System Date and Time: " + currentTime
                + memoryBlock
                + activeFilters;
        messages.add(ChatMessage.system(dynamicSystemPrompt));
        if (context != null && context.conversationHistory() != null && !context.conversationHistory().isEmpty()) {
            messages.addAll(context.conversationHistory());
        }
        return messages;
    }

    private String serializeResult(ToolResult<Object> result) {
        try {
            if (result.isSuccess()) {
                return objectMapper.writeValueAsString(Map.of("success", true, "data", result.getData()));
            }
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", Map.of("code", "TOOL_ERROR", "message", result.getErrorMessage())));
        } catch (Exception ex) {
            log.error("TravelAgent: failed to serialize tool result", ex);
            return "{\"success\":false,\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Result could not be processed.\"}}";
        }
    }

    private String buildErrorJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", Map.of("code", code, "message", message)));
        } catch (Exception ex) {
            return "{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"An internal error occurred.\"}}";
        }
    }

    private String buildToolCallJson(LlmToolCall toolCall) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tool_call", Map.of("name", toolCall.name(), "arguments", toolCall.arguments())));
        } catch (Exception ex) {
            return "Tool call: " + toolCall.name();
        }
    }

    private AgentResponse failureResponse(String message, List<String> executedTools) {
        String formattedJson = com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter.formatResponse(
                null,
                List.of(com.luna.aggarly.aiagent.engine.model.LumenResponseBlock.error("ERROR", message))
        );
        return AgentResponse.builder()
                .text(formattedJson)
                .toolCalls(List.copyOf(executedTools))
                .build();
    }
}
