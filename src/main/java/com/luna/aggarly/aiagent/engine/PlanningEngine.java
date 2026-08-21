package com.luna.aggarly.aiagent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.agent.Agent;
import com.luna.aggarly.aiagent.engine.model.LumenAgentResponse;
import com.luna.aggarly.aiagent.engine.model.LumenResponseBlock;
import com.luna.aggarly.aiagent.engine.model.LumenResponseFormatter;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.engine.records.SupervisorDecision;
import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlanningEngine {

    private static final int MAX_SUPERVISOR_TURNS = 4;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Agent> agentRegistry;
    private final String planningModel;

    public PlanningEngine(
            LlmClient llmClient,
            List<Agent> agents,
            @Value("${aggarly.ai.planning-model:nemotron-3-super:cloud}") String planningModel) {
        this.llmClient = llmClient;
        this.planningModel = planningModel;
        this.objectMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.agentRegistry = agents.stream()
                .collect(Collectors.toMap(Agent::name, Function.identity()));

        log.info("PlanningEngine initialized with agents: {} using planning model: {}", agentRegistry.keySet(), planningModel);
    }

    public AgentResponse executePlan(ClassifiedIntent intent, ConversationContext context, UserPrincipal user) {
        log.info("PlanningEngine dynamically executing plan for: {}", intent.rawMessage());

        String agentCapabilities = agentRegistry.values().stream()
                .map(a -> "- " + a.name() + ": " + a.description() + "\n  (Supported Tools: " + String.join(", ", a.supportedTools()) + ")")
                .collect(Collectors.joining("\n"));

        String supervisorSystemPrompt = """
            You are the Dynamic Planning Supervisor for Aggarly's luxury rental and concierge platform.
            Your responsibility is to fulfill the user's multi-step request by delegating sub-tasks to specialized agents ONE AT A TIME.

            Specialized Agents & Their Roles:
            %s

            ================================================================
            STANDARD MULTI-STEP WORKFLOW PATTERNS:
            ================================================================
            1. Search & Book Workflow (e.g. "Find property for 2 guests then book from Jan 1-6"):
               - Step 1: Delegate to PropertyAgent to search listings for the requested location/dates/guests.
               - Step 2: Once a property is found in the results, extract its exact 'Property ID', check-in date, check-out date, and guest count. Then delegate to BookingAgent:
                 Task: "Create booking for property ID <propertyId> for <guests> guests from <checkIn> to <checkOut>"
               - Step 3: Output DONE once BookingAgent processes the booking or requests user confirmation.

            2. Search & Travel / Weather Workflow (e.g. "Find villas in Santorini and check weather"):
               - Step 1: Delegate to PropertyAgent to find listings in the destination.
               - Step 2: Delegate to TravelAgent to look up destination weather, attractions, or dining.
               - Step 3: Output DONE with a combined summary.

            ================================================================
            LOOP PREVENTION RULES:
            ================================================================
            - NEVER delegate the exact same search or task to PropertyAgent multiple times. If PropertyAgent already found a property, PROCEED TO THE NEXT LOGICAL STEP (e.g. BookingAgent, TravelAgent, or DONE).
            - Always extract and pass discovered Property IDs (UUIDs), dates, and parameters in 'taskDescription'.
            - When all sub-tasks are addressed, output DONE immediately.

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

        Set<String> previousDelegations = new HashSet<>();
        String lastAgentNarrative = null;

        for (int turn = 1; turn <= MAX_SUPERVISOR_TURNS; turn++) {
            log.info("PlanningEngine turn {}/{}", turn, MAX_SUPERVISOR_TURNS);

            List<ChatMessage> decisionMessages = List.of(
                    ChatMessage.system(supervisorSystemPrompt),
                    ChatMessage.user("Global Execution Context:\n" + globalContext.toString() + "\n\nWhat is the single next step? Output JSON only.")
            );

            String decisionRaw = llmClient.chat(decisionMessages, planningModel);
            SupervisorDecision decision = parseSupervisorDecision(decisionRaw);

            if (decision == null || "DONE".equalsIgnoreCase(decision.action())) {
                String summaryText = decision != null && decision.finalSummary() != null && !decision.finalSummary().isBlank()
                        ? decision.finalSummary()
                        : (lastAgentNarrative != null ? lastAgentNarrative : (decisionRaw != null && !decisionRaw.isBlank() ? decisionRaw : "I have completed your multi-step request."));

                log.info("PlanningEngine completed execution gracefully on turn {}", turn);
                String formattedJson = LumenResponseFormatter.formatResponse(summaryText, collectedBlocks);

                return AgentResponse.builder()
                        .text(formattedJson)
                        .toolCalls(List.copyOf(allExecutedTools))
                        .metadataJson(serializeMetadata(collectedMetadata))
                        .build();
            }

            if ("DELEGATE".equalsIgnoreCase(decision.action())) {
                String targetAgentName = decision.agentName();
                String taskDesc = decision.taskDescription() != null ? decision.taskDescription().trim() : "";
                String delegationSignature = targetAgentName + ":" + taskDesc.toLowerCase();

                // Loop Detection: prevent repeating identical delegation
                if (previousDelegations.contains(delegationSignature)) {
                    log.warn("PlanningEngine detected repeated delegation: {}. Breaking loop and finalizing.", delegationSignature);
                    String summaryText = lastAgentNarrative != null
                            ? lastAgentNarrative
                            : "I have gathered the available details for your request.";
                    String formattedJson = LumenResponseFormatter.formatResponse(summaryText, collectedBlocks);
                    return AgentResponse.builder()
                            .text(formattedJson)
                            .toolCalls(List.copyOf(allExecutedTools))
                            .metadataJson(serializeMetadata(collectedMetadata))
                            .build();
                }
                previousDelegations.add(delegationSignature);

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

                try {
                    AgentResponse stepResponse = targetAgent.handle(subIntent, context, user);

                    if (stepResponse.getToolCalls() != null) {
                        allExecutedTools.addAll(stepResponse.getToolCalls());
                    }

                    // If subagent triggered a confirmation gate (e.g. BookingAgent requires confirmation), suspend immediately and present gate
                    if (stepResponse.isRequiresConfirmation()) {
                        log.info("Sub-agent {} requires user confirmation. Returning confirmation gate immediately.", targetAgentName);
                        return AgentResponse.awaitingConfirmation(
                                stepResponse.getText(),
                                stepResponse.getConfirmationToken(),
                                stepResponse.getPendingToolName(),
                                allExecutedTools
                        );
                    }

                    // Extract structured facts & clean narrative
                    String stepRawText = stepResponse.getText();
                    ParsedAgentOutput parsedOutput = extractNarrativeAndBlocks(stepRawText);

                    lastAgentNarrative = parsedOutput.narrative();

                    // Deduplicate and collect blocks
                    addDeduplicatedBlocks(collectedBlocks, parsedOutput.blocks());

                    globalContext.append(String.format(
                            "Result from %s:\n%s\n\n",
                            targetAgentName,
                            parsedOutput.narrative()
                    ));

                    // If structured entities were discovered, append explicit summary to global context
                    if (!parsedOutput.discoveredEntities().isEmpty()) {
                        globalContext.append("Discovered Entities for Next Steps:\n")
                                .append(parsedOutput.discoveredEntities())
                                .append("\n\n");
                    }

                } catch (Exception ex) {
                    log.error("Agent {} threw an exception during delegation", targetAgentName, ex);
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
        String formattedJson = LumenResponseFormatter.formatResponse(finalSummary, collectedBlocks);

        return AgentResponse.builder()
                .text(formattedJson)
                .toolCalls(List.copyOf(allExecutedTools))
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
