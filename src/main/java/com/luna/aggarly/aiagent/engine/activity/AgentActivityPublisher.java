package com.luna.aggarly.aiagent.engine.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.dto.activity.AgentActivityBlock;
import com.luna.aggarly.aiagent.dto.activity.AgentActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentActivityPublisher {

    private static final ThreadLocal<List<AgentActivityEvent>> CURRENT_REQUEST_EVENTS = ThreadLocal.withInitial(java.util.ArrayList::new);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    public static void startRecording() {
        CURRENT_REQUEST_EVENTS.get().clear();
    }

    public static List<AgentActivityEvent> getRecordedEvents() {
        return new java.util.ArrayList<>(CURRENT_REQUEST_EVENTS.get());
    }

    public static void clearRecording() {
        CURRENT_REQUEST_EVENTS.get().clear();
        CURRENT_REQUEST_EVENTS.remove();
    }

    public void publishThinking(UUID conversationId, String message) {
        publishThinkingStart(conversationId, message);
    }

    public void publishThinkingStart(UUID conversationId, String userMessage) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.thinkingStart(conversationId, userMessage);
        broadcast(conversationId, event);
    }

    public void publishThinkingEnd(UUID conversationId, long durationMs, String summary) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.thinkingEnd(conversationId, durationMs, summary);
        broadcast(conversationId, event);
    }

    public void publishIntentStart(UUID conversationId, String userMessage) {
        // Intent notification suppressed per requirements
    }

    public void publishIntentClassified(UUID conversationId, String category, String agentName) {
        // Intent notification suppressed per requirements
    }

    public void publishIntentEnd(UUID conversationId, String category, Double confidence, String agentName, long durationMs) {
        // Intent notification suppressed per requirements
    }

    public void publishTurnStart(UUID conversationId, String agentName, int turn, int maxTurns) {
        publishTurnStart(conversationId, agentName, turn, maxTurns, 0, 0);
    }

    public void publishTurnStart(UUID conversationId, String agentName, int turn, int maxTurns, int messagesCount, int toolsCount) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.turnStart(conversationId, agentName, turn, maxTurns, messagesCount, toolsCount);
        broadcast(conversationId, event);
    }

    public void publishTurnEnd(UUID conversationId, String agentName, int turn, int maxTurns, long durationMs, List<String> proposedTools) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.turnEnd(conversationId, agentName, turn, maxTurns, durationMs, proposedTools);
        broadcast(conversationId, event);
    }

    public void publishToolStart(UUID conversationId, String agentName, String toolName, Map<String, Object> arguments) {
        publishToolStart(conversationId, agentName, toolName, arguments, null);
    }

    public void publishToolStart(UUID conversationId, String agentName, String toolName, Map<String, Object> arguments, Integer turn) {
        if (conversationId == null) return;
        String friendlyTitle = getFriendlyToolTitle(toolName, arguments);
        String inputSummary = formatInputSummary(arguments);
        AgentActivityEvent event = AgentActivityEvent.toolStart(conversationId, agentName, toolName, friendlyTitle, inputSummary, turn);
        broadcast(conversationId, event);
    }

    public void publishToolEnd(UUID conversationId, String agentName, String toolName, long durationMs, Object result, boolean success) {
        publishToolEnd(conversationId, agentName, toolName, durationMs, null, result, success, null);
    }

    public void publishToolEnd(UUID conversationId, String agentName, String toolName, long durationMs, Object result, boolean success, Integer turn) {
        publishToolEnd(conversationId, agentName, toolName, durationMs, null, result, success, turn);
    }

    public void publishToolEnd(UUID conversationId, String agentName, String toolName, long durationMs, Map<String, Object> arguments, Object result, boolean success, Integer turn) {
        if (conversationId == null) return;
        String friendlyTitle = getFriendlyToolCompletionTitle(toolName, success, result);
        String inputSummary = formatInputSummary(arguments);
        String resultSummary = formatResultSummary(result, success);
        AgentActivityEvent event = AgentActivityEvent.toolEnd(conversationId, agentName, toolName, friendlyTitle, durationMs, inputSummary, resultSummary, result, success, turn);
        broadcast(conversationId, event);
    }

    public void publishSynthesizing(UUID conversationId, String agentName) {
        publishSynthesisStart(conversationId, agentName);
    }

    public void publishSynthesisStart(UUID conversationId, String agentName) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.synthesisStart(conversationId, agentName);
        broadcast(conversationId, event);
    }

    public void publishSynthesisEnd(UUID conversationId, String agentName, long durationMs, String summary) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.synthesisEnd(conversationId, agentName, durationMs, summary);
        broadcast(conversationId, event);
    }

    public void publishCompleted(UUID conversationId, String agentName, long totalDurationMs, int toolsCount) {
        publishCompleted(conversationId, agentName, totalDurationMs, toolsCount, 1);
    }

    public void publishCompleted(UUID conversationId, String agentName, long totalDurationMs, int toolsCount, int turnsCount) {
        if (conversationId == null) return;
        AgentActivityEvent event = AgentActivityEvent.completed(conversationId, agentName, totalDurationMs, toolsCount, turnsCount);
        broadcast(conversationId, event);
    }

    public AgentActivityBlock createActivityBlock(String toolName, long durationMs, Map<String, Object> arguments, Object result, boolean success) {
        return new AgentActivityBlock(
                toolName,
                getFriendlyToolTitle(toolName, arguments),
                success ? "COMPLETED" : "FAILED",
                durationMs,
                formatInputSummary(arguments),
                formatResultSummary(result, success),
                java.time.Instant.now()
        );
    }

    private void broadcast(UUID conversationId, AgentActivityEvent event) {
        try {
            List<AgentActivityEvent> list = CURRENT_REQUEST_EVENTS.get();
            if (event.id() != null) {
                int existingIdx = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (event.id().equals(list.get(i).id())) {
                        existingIdx = i;
                        break;
                    }
                }
                if (existingIdx != -1) {
                    list.set(existingIdx, event);
                } else {
                    list.add(event);
                }
            } else {
                list.add(event);
            }
        } catch (Exception ignored) {}

        try {
            if (stringRedisTemplate != null) {
                try {
                    String json = objectMapper.writeValueAsString(event);
                    stringRedisTemplate.convertAndSend("aggarly:chat:activities", json);
                    log.debug("Published AI Activity to Redis for conversation {}", conversationId);
                    return;
                } catch (Exception re) {
                    log.debug("Redis publish failed, falling back to direct STOMP: {}", re.getMessage());
                }
            }
            dispatchDirectStomp(conversationId, event);
        } catch (Exception ex) {
            log.warn("Failed to broadcast AI activity event to {}: {}", conversationId, ex.getMessage());
        }
    }

    public void dispatchDirectStomp(UUID conversationId, AgentActivityEvent event) {
        if (conversationId != null) {
            messagingTemplate.convertAndSend("/topic/conversation." + conversationId, event);
        } else {
            messagingTemplate.convertAndSend("/topic/conversation.00000000-0000-0000-0000-000000000000", event);
        }
        log.info("Broadcasted AI Activity to conversation {}: {} ({})", conversationId, event.friendlyTitle(), event.activityType());
    }

    public String getFriendlyToolTitle(String toolName, Map<String, Object> arguments) {
        if (toolName == null) return "Executing AI operation...";
        return switch (toolName) {
            case "property.search" -> {
                String city = arguments != null && arguments.get("city") != null ? arguments.get("city").toString() : null;
                yield city != null ? ("🔍 Searching properties in " + city) : "🔍 Searching available property listings";
            }
            case "property.info", "property.details" -> "🏠 Fetching complete property specifications";
            case "property.calendar", "property.availability", "booking.availability" -> "📅 Verifying property calendar availability";
            case "booking.priceExplanation", "calculatePriceTool" -> "💰 Calculating rates, cleaning fees & taxes";
            case "booking.create" -> "📝 Initializing booking reservation";
            case "booking.status" -> "📋 Retrieving booking reservation details";
            case "vision.searchByText", "vision.searchByImage", "vision.searchMultimodal", "vision.search", "visionSearchByTextTool", "visionSearchByImageTool" -> "✨ Running multimodal visual similarity match";
            case "messaging.readConversation" -> "📜 Reading full conversation history transcript";
            case "messaging.sendToHost" -> "💬 Dispatching message to property host";
            case "messaging.summary" -> "📝 Summarizing conversation thread";
            case "cleaning.tasks" -> "🧹 Reviewing turnover cleaning schedule";
            case "user.wishlist.update" -> "💖 Updating your saved properties";
            case "review.insights" -> "⭐ Analyzing guest reviews & ratings";
            case "schedule.control" -> "⚙️ Updating your scheduled automation";
            case "schedule.preview" -> "🔍 Simulating workflow execution preview";
            case "vision.compareProperties" -> "🖼️ Comparing properties visually";
            case "booking.cancellationQuote" -> "🔄 Calculating cancellation & refund policy quote";
            case "support.faq" -> "❓ Searching platform knowledge base & FAQ";
            default -> "⚡ Executing: " + humanizeCamelCase(toolName);
        };
    }

    public String getFriendlyToolCompletionTitle(String toolName, boolean success, Object result) {
        if (!success) return "❌ Operation failed: " + humanizeCamelCase(toolName);
        return switch (toolName) {
            case "property.search" -> {
                int count = extractCountFromResult(result);
                yield count > 0 ? ("✓ Found " + count + " matching properties") : "✓ Properties search complete";
            }
            case "property.info", "property.details" -> "✓ Property details loaded";
            case "property.calendar", "property.availability", "booking.availability" -> "✓ Availability verified";
            case "booking.priceExplanation", "calculatePriceTool" -> "✓ Price calculation computed";
            case "booking.create" -> "✓ Reservation created successfully";
            case "vision.searchByText", "vision.searchByImage", "vision.searchMultimodal", "vision.search" -> "✓ Visual match complete";
            case "messaging.readConversation" -> "✓ Retrieved conversation transcript";
            case "messaging.sendToHost" -> "✓ Message sent to host";
            case "user.wishlist.update" -> "✓ Wishlist updated";
            case "cleaning.tasks" -> "✓ Cleaning tasks retrieved";
            case "review.insights" -> "✓ Review insights analyzed";
            case "schedule.control" -> "✓ Scheduled task updated";
            case "schedule.preview" -> "✓ Workflow preview computed";
            default -> "✓ " + humanizeCamelCase(toolName) + " finished";
        };
    }

    private int extractCountFromResult(Object result) {
        if (result == null) return 0;
        try {
            if (result instanceof com.luna.aggarly.aiagent.tool.property.PropertySearchTool.Response resp && resp.properties() != null) {
                return resp.properties().size();
            }
            if (result instanceof java.util.Collection<?> col) {
                return col.size();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatInputSummary(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return arguments.toString();
        }
    }

    private String formatResultSummary(Object result, boolean success) {
        if (!success) return "Execution encountered an issue";
        if (result == null) return "Finished successfully";
        try {
            if (result instanceof String s) return s;
            String json = objectMapper.writeValueAsString(result);
            if (json.length() > 500) {
                return json.substring(0, 497) + "...";
            }
            return json;
        } catch (Exception e) {
            return result.toString();
        }
    }

    private String humanizeCamelCase(String str) {
        if (str == null || str.isBlank()) return "";
        return str.replaceAll("Tool$", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .substring(0, 1).toUpperCase() + str.substring(1).replaceAll("Tool$", "").replaceAll("([a-z])([A-Z])", "$1 $2");
    }
}
