package com.luna.aggarly.aiagent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.agent.Agent;
import com.luna.aggarly.aiagent.engine.activity.AgentActivityPublisher;
import com.luna.aggarly.aiagent.engine.model.LumenAgentResponse;
import com.luna.aggarly.aiagent.engine.model.LumenResponseBlock;
import com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter;
import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.engine.records.SupervisorDecision;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlanningEngine {

    private static final int MAX_SUPERVISOR_TURNS = 5;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Agent> agentRegistry;
    private final String planningModel;
    private final AgentActivityPublisher activityPublisher;

    public PlanningEngine(
            LlmClient llmClient,
            List<Agent> agents,
            @Autowired(required = false) AgentActivityPublisher activityPublisher,
            @Value("${aggarly.ai.planning-model:nemotron-3-super:cloud}") String planningModel) {
        this.llmClient = llmClient;
        this.activityPublisher = activityPublisher;
        this.planningModel = planningModel;
        this.objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.agentRegistry = agents.stream()
                .collect(Collectors.toMap(Agent::name, Function.identity()));

        log.info("PlanningEngine initialized with agents: {} using planning model: {}", agentRegistry.keySet(), planningModel);
    }

    public AgentResponse executePlan(ClassifiedIntent intent, ConversationContext context, UserPrincipal user) {
        log.info("PlanningEngine dynamically executing multi-step plan for: {}", intent.rawMessage());
        long planStartTime = System.currentTimeMillis();
        UUID convId = context != null ? context.conversationId() : null;

        if (activityPublisher != null && convId != null) {
            activityPublisher.publishThinkingStart(convId, "🧠 Planning multi-agent execution strategy...");
        }

        String agentCapabilities = agentRegistry.values().stream()
                .map(a -> "- " + a.name() + ": " + a.description() + "\n  (Supported Tools: " + String.join(", ", a.supportedTools()) + ")")
                .collect(Collectors.joining("\n"));

        String supervisorSystemPrompt = """
            You are the Dynamic Planning Supervisor for Aggarly's luxury rental and AI concierge platform.
            Your responsibility is to fulfill complex multi-step user requests by delegating sub-tasks to specialized agents ONE AT A TIME.

            Specialized Agents & Their Roles:
            %s

            ================================================================
            STANDARD MULTI-STEP WORKFLOW PATTERNS:
            ================================================================
            1. Search & Book Workflow (e.g. "Find villa in Alexandria and reserve for 3 guests"):
               - Step 1: Delegate to PropertyAgent to search listings for the requested location/dates/guests.
               - Step 2: Once a property is found in the results, extract its exact 'Property ID', dates, and guests. Then delegate to BookingAgent:
                 Task: "Create booking for property ID <propertyId> for <guests> guests from <checkIn> to <checkOut>"
               - Step 3: Output DONE once BookingAgent processes the booking or requests user confirmation.

            2. Search & Visual Exploration Workflow (e.g. "Find penthouses with sunset sea views"):
               - Step 1: Delegate to PropertyAgent to search matching listings.
               - Step 2: Delegate to VisionAgent to run multimodal aesthetic and room tour similarity analysis.
               - Step 3: Output DONE with rich visual recommendations.

            3. Search & Travel Itinerary Workflow (e.g. "Find villas in Santorini and plan dining & weather"):
               - Step 1: Delegate to PropertyAgent to find listings in the destination.
               - Step 2: Delegate to TravelAgent to look up destination weather, attractions, or dining.
               - Step 3: Output DONE with a combined curated overview.

            4. Automated Scheduling Workflow (e.g. "Monitor prices in Mykonos and alert me every Monday"):
               - Step 1: Delegate to PropertyAgent to find target properties.
               - Step 2: Delegate to SchedulingAgent to compile and register the recurring schedule.
               - Step 3: Output DONE.

            5. Host Management & Optimization:
               - Delegate to HostAgent to analyze occupancy, dynamic pricing, earnings, or block calendar.

            6. Administrative Tasks & Moderation:
               - Delegate to AdminAgent for coupon creation, content moderation, or OCR extraction.

            ================================================================
            CRITICAL SUPERVISOR GUARDRAILS (STRICT):
            ================================================================
            1. MAXIMUM ONE DELEGATION PER AGENT: Never delegate to the same agent more than once.
               - If PropertyAgent already found properties, do NOT invoke PropertyAgent again.
               - If BookingAgent already checked availability or initialized a booking, do NOT invoke BookingAgent again.
            2. IMMEDIATE TERMINATION ON CONFIRMATION: If an agent states that an action requires confirmation or is pending confirmation (e.g. Booking confirmation required, cancellation quote, payment), you MUST IMMEDIATELY OUTPUT {"action": "DONE", ...}. NEVER try to re-delegate to any agent when confirmation is pending.
            3. ALL-IN-ONE BOOKING INSTRUCTIONS: When delegating booking creation to BookingAgent, include all parameters at once: "Create booking for property ID <uuid> from <checkIn> to <checkOut> for <guests> guests". BookingAgent will handle the reservation atomically.
            4. ONCE ALL INFO IS RETRIEVED: Output {"action": "DONE", "finalSummary": "..."} immediately. Repeating delegations to the same agent is strictly forbidden.

            ================================================================
            OUTPUT FORMAT:
            ================================================================
            You must output ONLY a valid JSON object matching one of the following two schemas:

            Format 1 (To delegate a sub-task):
            {
              "action": "DELEGATE",
              "agentName": "<ExactAgentName>",
              "taskDescription": "<Detailed instruction with all required parameters and context>"
            }

            Format 2 (When finished):
            {
              "action": "DONE",
              "finalSummary": "<Complete, polished response synthesizing all findings. Start with an evocative editorial quote enclosed in quotation marks on the very first line, e.g. \\"A curated Mediterranean escape crafted for your journey.\\" followed by \\n\\n and the full structured overview.>"
            }

            Current System Timestamp: %s
            """.formatted(agentCapabilities, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        StringBuilder globalContext = new StringBuilder();
        if (context != null && context.userMemories() != null && !context.userMemories().isEmpty()) {
            globalContext.append("User Confirmed Preferences & Long-term Memories:\n");
            for (var mem : context.userMemories()) {
                globalContext.append("- ").append(mem.getMemoryKey()).append(": ").append(mem.getMemoryValue()).append("\n");
            }
            globalContext.append("\n");
        }
        globalContext.append("User's Original Request: ").append(intent.rawMessage()).append("\n\n");

        List<String> allExecutedTools = new ArrayList<>();
        List<LumenResponseBlock> collectedBlocks = new ArrayList<>();
        Map<String, Object> collectedMetadata = new HashMap<>();
        List<Map<String, Object>> executionPlanSteps = new ArrayList<>();

        Set<String> previousDelegations = new HashSet<>();
        Map<String, Integer> agentDelegationCounts = new HashMap<>();
        String lastAgentNarrative = null;

        for (int turn = 1; turn <= MAX_SUPERVISOR_TURNS; turn++) {
            log.info("PlanningEngine turn {}/{}", turn, MAX_SUPERVISOR_TURNS);
            long turnStartTime = System.currentTimeMillis();

            if (activityPublisher != null && convId != null) {
                activityPublisher.publishTurnStart(convId, "SupervisorPlanner", turn, MAX_SUPERVISOR_TURNS, 2, agentRegistry.size());
            }

            List<ChatMessage> decisionMessages = List.of(
                    ChatMessage.system(supervisorSystemPrompt),
                    ChatMessage.user("Global Execution Context:\n" + globalContext.toString() + "\n\nWhat is the single next step? Output JSON only.")
            );

            String decisionRaw = llmClient.chat(decisionMessages, planningModel);
            SupervisorDecision decision = parseSupervisorDecision(decisionRaw);

            long turnDuration = System.currentTimeMillis() - turnStartTime;
            if (activityPublisher != null && convId != null) {
                List<String> proposed = decision != null && "DELEGATE".equalsIgnoreCase(decision.action()) && decision.agentName() != null
                        ? List.of("delegate:" + decision.agentName())
                        : List.of();
                activityPublisher.publishTurnEnd(convId, "SupervisorPlanner", turn, MAX_SUPERVISOR_TURNS, turnDuration, proposed);
            }

            if (decision == null || "DONE".equalsIgnoreCase(decision.action())) {
                String summaryText = decision != null && decision.finalSummary() != null && !decision.finalSummary().isBlank()
                        ? decision.finalSummary()
                        : (lastAgentNarrative != null ? lastAgentNarrative : (decisionRaw != null && !decisionRaw.isBlank() ? decisionRaw : "I have completed your multi-step request."));

                log.info("PlanningEngine completed execution gracefully on turn {}", turn);
                return finalizePlanResponse(summaryText, collectedBlocks, allExecutedTools, collectedMetadata, executionPlanSteps, planStartTime, convId, turn);
            }

            if ("DELEGATE".equalsIgnoreCase(decision.action())) {
                String targetAgentName = decision.agentName();
                String taskDesc = decision.taskDescription() != null ? decision.taskDescription().trim() : "";
                String delegationSignature = targetAgentName + ":" + taskDesc.toLowerCase();

                // Loop Detection: prevent repeating identical delegation or delegating multiple times to the same agent
                int count = agentDelegationCounts.getOrDefault(targetAgentName, 0);
                if (count >= 1 || previousDelegations.contains(delegationSignature)) {
                    log.warn("PlanningEngine detected repeated/redundant delegation to agent '{}' (count={}). Breaking loop and finalizing.", targetAgentName, count);
                    String summaryText = lastAgentNarrative != null
                            ? lastAgentNarrative
                            : "I have gathered the available details for your request.";
                    return finalizePlanResponse(summaryText, collectedBlocks, allExecutedTools, collectedMetadata, executionPlanSteps, planStartTime, convId, turn);
                }
                previousDelegations.add(delegationSignature);
                agentDelegationCounts.put(targetAgentName, count + 1);

                log.info("PlanningEngine delegating to {}: {}", targetAgentName, taskDesc);

                Agent targetAgent = agentRegistry.get(targetAgentName);
                if (targetAgent == null) {
                    targetAgent = agentRegistry.entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase(targetAgentName))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
                }

                if (targetAgent == null) {
                    log.warn("PlanningEngine: unknown agent '{}'", targetAgentName);
                    globalContext.append(String.format("Step failed: Unknown agent '%s'. Available agents are: %s\n",
                            targetAgentName, String.join(", ", agentRegistry.keySet())));
                    continue;
                }

                String subAgentInstruction = String.format(
                        "Task: %s\n\nGlobal Execution Context for Reference:\n%s",
                        taskDesc,
                        globalContext.toString()
                );

                ClassifiedIntent subIntent = new ClassifiedIntent(
                        intent.category(),
                        subAgentInstruction,
                        context
                );

                long stepStartTime = System.currentTimeMillis();
                try {
                    AgentResponse stepResponse = targetAgent.handle(subIntent, context, user);
                    long stepDuration = System.currentTimeMillis() - stepStartTime;

                    List<String> stepTools = stepResponse.getToolCalls() != null ? stepResponse.getToolCalls() : List.of();
                    allExecutedTools.addAll(stepTools);

                    // Extract structured facts & clean narrative
                    String stepRawText = stepResponse.getText();
                    ParsedAgentOutput parsedOutput = extractNarrativeAndBlocks(stepRawText);
                    lastAgentNarrative = parsedOutput.narrative();

                    // Record execution step in plan
                    Map<String, Object> planStep = new LinkedHashMap<>();
                    planStep.put("stepNumber", executionPlanSteps.size() + 1);
                    planStep.put("agentName", targetAgentName);
                    planStep.put("task", taskDesc);
                    planStep.put("status", "COMPLETED");
                    planStep.put("durationMs", stepDuration);
                    planStep.put("toolsExecuted", stepTools);
                    planStep.put("summary", parsedOutput.narrative());
                    executionPlanSteps.add(planStep);

                    // Deduplicate and collect blocks
                    addDeduplicatedBlocks(collectedBlocks, parsedOutput.blocks());

                    // If subagent triggered a confirmation gate, finalize with rich confirmation block
                    if (stepResponse.isRequiresConfirmation()) {
                        log.info("Sub-agent {} requires user confirmation for {}. Returning rich confirmation gate.",
                                targetAgentName, stepResponse.getPendingToolName());

                        Map<String, Object> confData = new LinkedHashMap<>();
                        confData.put("title", "Booking Confirmation Required");
                        confData.put("message", parsedOutput.narrative() != null && !parsedOutput.narrative().isBlank()
                                ? parsedOutput.narrative()
                                : "Creating this booking requires your confirmation. Please confirm that you want to proceed.");
                        confData.put("toolName", stepResponse.getPendingToolName());
                        confData.put("pendingToolName", stepResponse.getPendingToolName());
                        confData.put("confirmationToken", stepResponse.getConfirmationToken());
                        confData.put("confirmEndpoint", "/api/v1/ai/confirm/" + stepResponse.getConfirmationToken());
                        confData.put("rejectEndpoint", "/api/v1/ai/reject/" + stepResponse.getConfirmationToken());
                        confData.put("actionType", stepResponse.getPendingToolName() != null ? stepResponse.getPendingToolName().toUpperCase().replace('.', '_') : "BOOKING_CREATE");
                        confData.put("confirmLabel", "Confirm & Proceed");
                        confData.put("cancelLabel", "Cancel");

                        if (!containsConfirmation(collectedBlocks, stepResponse.getConfirmationToken())) {
                            collectedBlocks.add(LumenResponseBlock.confirmation(confData));

                            collectedBlocks.add(LumenResponseBlock.actions(List.of(
                                    Map.of("id", "confirm-" + stepResponse.getConfirmationToken(), "label", "Confirm & Proceed", "variant", "primary", "action", "ai.confirm", "confirmationToken", stepResponse.getConfirmationToken()),
                                    Map.of("id", "cancel-" + stepResponse.getConfirmationToken(), "label", "Cancel", "variant", "secondary", "action", "ai.cancel", "confirmationToken", stepResponse.getConfirmationToken())
                            )));
                        }

                        return finalizePlanResponseWithConfirmation(
                                parsedOutput.narrative() != null ? parsedOutput.narrative() : "Creating this booking requires your confirmation. Please confirm that you want to proceed.",
                                collectedBlocks,
                                allExecutedTools,
                                collectedMetadata,
                                executionPlanSteps,
                                planStartTime,
                                convId,
                                turn,
                                stepResponse.getConfirmationToken(),
                                stepResponse.getPendingToolName()
                        );
                    }

                    globalContext.append(String.format(
                            "Result from %s:\n%s\n\n",
                            targetAgentName,
                            parsedOutput.narrative()
                    ));

                    if (!parsedOutput.discoveredEntities().isEmpty()) {
                        globalContext.append("Discovered Entities for Next Steps:\n")
                                .append(parsedOutput.discoveredEntities())
                                .append("\n\n");
                    }

                } catch (Exception ex) {
                    log.error("Agent {} threw an exception during delegation", targetAgentName, ex);
                    long stepDuration = System.currentTimeMillis() - stepStartTime;

                    Map<String, Object> planStep = new LinkedHashMap<>();
                    planStep.put("stepNumber", executionPlanSteps.size() + 1);
                    planStep.put("agentName", targetAgentName);
                    planStep.put("task", taskDesc);
                    planStep.put("status", "FAILED");
                    planStep.put("durationMs", stepDuration);
                    planStep.put("error", ex.getMessage());
                    executionPlanSteps.add(planStep);

                    globalContext.append(String.format("Delegation to %s encountered an error: %s\n", targetAgentName, ex.getMessage()));
                }
            } else {
                log.warn("PlanningEngine received unrecognized action '{}'", decision.action());
                globalContext.append(String.format("Unrecognized action '%s'. Please output DELEGATE or DONE.\n", decision.action()));
            }
        }

        log.warn("PlanningEngine reached MAX_SUPERVISOR_TURNS ({})", MAX_SUPERVISOR_TURNS);
        String finalSummary = lastAgentNarrative != null
                ? lastAgentNarrative
                : "I have gathered the available options and details for your request.";

        return finalizePlanResponse(finalSummary, collectedBlocks, allExecutedTools, collectedMetadata, executionPlanSteps, planStartTime, convId, MAX_SUPERVISOR_TURNS);
    }

    private AgentResponse finalizePlanResponse(
            String summaryText,
            List<LumenResponseBlock> collectedBlocks,
            List<String> allExecutedTools,
            Map<String, Object> collectedMetadata,
            List<Map<String, Object>> executionPlanSteps,
            long planStartTime,
            UUID convId,
            int turnsCount
    ) {
        long totalDuration = System.currentTimeMillis() - planStartTime;

        // Persist structured Execution Plan as first block
        if (!executionPlanSteps.isEmpty()) {
            Map<String, Object> planBlockData = new LinkedHashMap<>();
            planBlockData.put("title", "Multi-Agent Execution Plan");
            planBlockData.put("totalSteps", executionPlanSteps.size());
            planBlockData.put("totalDurationMs", totalDuration);
            planBlockData.put("steps", executionPlanSteps);

            collectedBlocks.add(0, LumenResponseBlock.executionPlan(planBlockData));
            collectedMetadata.put("executionPlan", executionPlanSteps);
        }

        if (activityPublisher != null && convId != null) {
            activityPublisher.publishSynthesisStart(convId, "SupervisorPlanner");
            activityPublisher.publishSynthesisEnd(convId, "SupervisorPlanner", 150L, "Synthesized multi-agent results & attached execution plan");
            activityPublisher.publishCompleted(convId, "SupervisorPlanner", totalDuration, allExecutedTools.size(), turnsCount);
        }

        String formattedJson = LumenResponseFormatter.formatResponse(summaryText, collectedBlocks);

        return AgentResponse.builder()
                .text(formattedJson)
                .toolCalls(List.copyOf(allExecutedTools))
                .metadataJson(serializeMetadata(collectedMetadata))
                .build();
    }

    private AgentResponse finalizePlanResponseWithConfirmation(
            String summaryText,
            List<LumenResponseBlock> collectedBlocks,
            List<String> allExecutedTools,
            Map<String, Object> collectedMetadata,
            List<Map<String, Object>> executionPlanSteps,
            long planStartTime,
            UUID convId,
            int turnsCount,
            String confirmationToken,
            String pendingToolName
    ) {
        long totalDuration = System.currentTimeMillis() - planStartTime;

        if (!executionPlanSteps.isEmpty()) {
            Map<String, Object> planBlockData = new LinkedHashMap<>();
            planBlockData.put("title", "Multi-Agent Execution Plan");
            planBlockData.put("totalSteps", executionPlanSteps.size());
            planBlockData.put("totalDurationMs", totalDuration);
            planBlockData.put("steps", executionPlanSteps);

            collectedBlocks.add(0, LumenResponseBlock.executionPlan(planBlockData));
            collectedMetadata.put("executionPlan", executionPlanSteps);
        }

        if (activityPublisher != null && convId != null) {
            activityPublisher.publishSynthesisStart(convId, "SupervisorPlanner");
            activityPublisher.publishSynthesisEnd(convId, "SupervisorPlanner", 150L, "Synthesized multi-agent results & awaiting guest confirmation");
            activityPublisher.publishCompleted(convId, "SupervisorPlanner", totalDuration, allExecutedTools.size(), turnsCount);
        }

        String formattedJson = LumenResponseFormatter.formatResponse(summaryText, collectedBlocks);

        return AgentResponse.builder()
                .text(formattedJson)
                .toolCalls(List.copyOf(allExecutedTools))
                .requiresConfirmation(true)
                .confirmationToken(confirmationToken)
                .pendingToolName(pendingToolName)
                .metadataJson(serializeMetadata(collectedMetadata))
                .build();
    }

    private void addDeduplicatedBlocks(List<LumenResponseBlock> target, List<LumenResponseBlock> incoming) {
        if (incoming == null) return;
        for (LumenResponseBlock inc : incoming) {
            boolean duplicate = false;
            for (LumenResponseBlock existing : target) {
                if (Objects.equals(existing.getType(), inc.getType())) {
                    if (Objects.equals(existing.getData(), inc.getData())) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) {
                target.add(inc);
            }
        }
    }

    private boolean containsConfirmation(List<LumenResponseBlock> blocks, String confirmationToken) {
        if (confirmationToken == null) return false;
        return blocks.stream().anyMatch(b ->
                "confirmation".equals(b.getType())
                        && b.getData() != null
                        && confirmationToken.equals(String.valueOf(b.getData().get("confirmationToken"))));
    }

    private SupervisorDecision parseSupervisorDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            String jsonStr = extractJsonString(raw);
            if (jsonStr != null) {
                JsonNode root = objectMapper.readTree(jsonStr);
                String action = root.path("action").asText(null);
                String agentName = root.path("agentName").asText(null);
                String taskDescription = root.path("taskDescription").asText(null);
                String finalSummary = root.path("finalSummary").asText(null);

                if (action != null) {
                    return new SupervisorDecision(action, agentName, taskDescription, finalSummary);
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to parse supervisor decision as structured JSON: {}", ex.getMessage());
        }

        return new SupervisorDecision("DONE", null, null, raw.trim());
    }

    private String extractJsonString(String raw) {
        String trimmed = raw.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ParsedAgentOutput extractNarrativeAndBlocks(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ParsedAgentOutput("No response", List.of(), "");
        }

        List<LumenResponseBlock> nonTextBlocks = new ArrayList<>();
        StringBuilder narrative = new StringBuilder();
        StringBuilder entities = new StringBuilder();

        try {
            String jsonContent = extractJsonString(rawJson);
            if (jsonContent != null) {
                LumenAgentResponse resp = objectMapper.readValue(jsonContent, LumenAgentResponse.class);
                if (resp.getBlocks() != null) {
                    for (LumenResponseBlock block : resp.getBlocks()) {
                        if ("text".equalsIgnoreCase(block.getType())) {
                            if (block.getContent() != null && !block.getContent().isBlank()) {
                                if (narrative.length() > 0) narrative.append("\n");
                                narrative.append(block.getContent());
                            }
                        } else if ("execution_plan".equalsIgnoreCase(block.getType())) {
                            // Sub-agent execution plans are superseded by the supervisor's canonical plan
                            // (and the live trace attached by ConversationManager); keeping them duplicates cards.
                            log.debug("PlanningEngine dropped sub-agent execution_plan block: supervisor owns the plan");
                        } else {
                            nonTextBlocks.add(block);

                            // Extract property entities
                            if ("property".equalsIgnoreCase(block.getType()) && block.getData() != null) {
                                Map<String, Object> d = block.getData();
                                Object idVal = d.get("id");
                                Object titleVal = d.get("title");
                                Object priceVal = d.get("nightlyPrice") != null ? d.get("nightlyPrice") : d.get("price");
                                entities.append(String.format("- Property ID: %s | Title: %s | Price: €%s/night\n",
                                        idVal != null ? idVal : "",
                                        titleVal != null ? titleVal : "Property",
                                        priceVal != null ? priceVal : ""));
                            } else if ("property_list".equalsIgnoreCase(block.getType()) && block.getData() != null) {
                                Object props = block.getData().get("properties");
                                if (props instanceof List<?> list) {
                                    for (Object item : list) {
                                        if (item instanceof Map<?, ?> m) {
                                            Object idVal = m.get("id");
                                            Object titleVal = m.get("title");
                                            Object priceVal = m.get("nightlyPrice") != null ? m.get("nightlyPrice") : m.get("price");
                                            entities.append(String.format("- Property ID: %s | Title: %s | Price: €%s/night\n",
                                                    idVal != null ? idVal : "",
                                                    titleVal != null ? titleVal : "Property",
                                                    priceVal != null ? priceVal : ""));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse agent output as LumenAgentResponse", e);
        }

        if (narrative.length() == 0) {
            narrative.append(rawJson);
        }

        return new ParsedAgentOutput(narrative.toString(), nonTextBlocks, entities.toString().trim());
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }

    private record ParsedAgentOutput(String narrative, List<LumenResponseBlock> blocks, String discoveredEntities) {}
}
