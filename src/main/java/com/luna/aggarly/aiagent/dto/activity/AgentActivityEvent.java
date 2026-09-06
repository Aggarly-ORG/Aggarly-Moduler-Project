package com.luna.aggarly.aiagent.dto.activity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentActivityEvent(
        String id,
        String eventType,          // "AI_ACTIVITY"
        UUID conversationId,
        AgentActivityType activityType,
        String agentName,
        Integer turn,
        Integer maxTurns,
        String toolName,
        String friendlyTitle,
        String status,             // "RUNNING", "COMPLETED", "FAILED"
        Long durationMs,
        String inputSummary,
        String resultSummary,
        Object resultData,
        Instant timestamp
) {
    public static AgentActivityEvent thinkingStart(UUID conversationId, String userMessage) {
        return new AgentActivityEvent(
                "act-think",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.THINKING_START,
                "AI Concierge",
                null, null,
                null,
                "🧠 Analyzing your request & loading context...",
                "RUNNING",
                null,
                userMessage != null ? ("Query: \"" + (userMessage.length() > 100 ? userMessage.substring(0, 97) + "..." : userMessage) + "\"") : "Processing request",
                null,
                null,
                Instant.now()
        );
    }

    public static AgentActivityEvent thinkingEnd(UUID conversationId, long durationMs, String summary) {
        return new AgentActivityEvent(
                "act-think",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.THINKING_END,
                "AI Concierge",
                null, null,
                null,
                "✓ Request & memory context loaded",
                "COMPLETED",
                durationMs,
                null,
                summary != null ? summary : "Loaded user preferences, active search filters and memory",
                Map.of("status", "CONTEXT_READY"),
                Instant.now()
        );
    }

    public static AgentActivityEvent intentStart(UUID conversationId, String userMessage) {
        return new AgentActivityEvent(
                "act-intent",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.INTENT_START,
                "AgentRouter",
                null, null,
                null,
                "🧭 Classifying intent & selecting specialized agent...",
                "RUNNING",
                null,
                "Analyzing query semantics against platform domain agents (Property, Booking, Host, Support, Travel, Admin)",
                null,
                null,
                Instant.now()
        );
    }

    public static AgentActivityEvent intentEnd(UUID conversationId, String category, Double confidence, String agentName, long durationMs) {
        String confStr = confidence != null ? String.format(" (Confidence: %.0f%%)", confidence * 100) : "";
        return new AgentActivityEvent(
                "act-intent",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.INTENT_END,
                agentName,
                null, null,
                null,
                "✓ Identified Intent: " + category + confStr + " — Consulting " + agentName,
                "COMPLETED",
                durationMs,
                null,
                "Domain Category: " + category + " | Assigned Specialist: " + agentName,
                Map.of("category", category, "confidence", confidence != null ? confidence : 1.0, "agent", agentName),
                Instant.now()
        );
    }

    public static AgentActivityEvent turnStart(UUID conversationId, String agentName, int turn, int maxTurns, int messagesCount, int toolsCount) {
        return new AgentActivityEvent(
                "act-turn-" + turn,
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.TURN_START,
                agentName,
                turn, maxTurns,
                null,
                "🔄 Agent Loop (Turn " + turn + "/" + maxTurns + "): Reasoning with LLM...",
                "RUNNING",
                null,
                "Context: " + messagesCount + " messages in history | " + toolsCount + " registered domain tools available",
                null,
                null,
                Instant.now()
        );
    }

    public static AgentActivityEvent turnEnd(UUID conversationId, String agentName, int turn, int maxTurns, long durationMs, List<String> proposedTools) {
        boolean hasTools = proposedTools != null && !proposedTools.isEmpty();
        String title = hasTools
                ? ("✓ Agent Loop (Turn " + turn + "/" + maxTurns + "): Decided to call " + proposedTools.size() + " tool(s)")
                : ("✓ Agent Loop (Turn " + turn + "/" + maxTurns + "): Formulated final text response");
        String summary = hasTools
                ? ("Tools to invoke: " + String.join(", ", proposedTools))
                : "LLM finished reasoning and generated user-facing response";

        return new AgentActivityEvent(
                "act-turn-" + turn,
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.TURN_END,
                agentName,
                turn, maxTurns,
                null,
                title,
                "COMPLETED",
                durationMs,
                null,
                summary,
                Map.of("turn", turn, "toolsProposed", proposedTools != null ? proposedTools : List.of()),
                Instant.now()
        );
    }

    public static AgentActivityEvent toolStart(UUID conversationId, String agentName, String toolName, String friendlyTitle, String inputSummary, Integer turn) {
        return new AgentActivityEvent(
                "act-tool-" + (toolName != null ? toolName : "unknown"),
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.TOOL_START,
                agentName,
                turn, null,
                toolName,
                friendlyTitle != null ? friendlyTitle : ("⚡ Executing tool: " + toolName),
                "RUNNING",
                null,
                inputSummary,
                null,
                null,
                Instant.now()
        );
    }

    public static AgentActivityEvent toolEnd(UUID conversationId, String agentName, String toolName, String friendlyTitle, long durationMs, String inputSummary, String resultSummary, Object resultData, boolean success, Integer turn) {
        return new AgentActivityEvent(
                "act-tool-" + (toolName != null ? toolName : "unknown"),
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.TOOL_END,
                agentName,
                turn, null,
                toolName,
                friendlyTitle != null ? friendlyTitle : ("✓ Completed: " + toolName),
                success ? "COMPLETED" : "FAILED",
                durationMs,
                inputSummary,
                resultSummary,
                resultData,
                Instant.now()
        );
    }

    public static AgentActivityEvent synthesisStart(UUID conversationId, String agentName) {
        return new AgentActivityEvent(
                "act-synth",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.SYNTHESIS_START,
                agentName,
                null, null,
                null,
                "✍️ Formulating personalized recommendations & interactive UI...",
                "RUNNING",
                null,
                "Synthesizing verified tool outputs and composing structured UI cards",
                null,
                null,
                Instant.now()
        );
    }

    public static AgentActivityEvent synthesisEnd(UUID conversationId, String agentName, long durationMs, String summary) {
        return new AgentActivityEvent(
                "act-synth",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.SYNTHESIS_END,
                agentName,
                null, null,
                null,
                "✓ Final response & rich cards formulated",
                "COMPLETED",
                durationMs,
                null,
                summary != null ? summary : "Generated final answer and attached interactive blocks",
                Map.of("status", "SYNTHESIS_COMPLETE", "agent", agentName),
                Instant.now()
        );
    }

    public static AgentActivityEvent completed(UUID conversationId, String agentName, long totalDurationMs, int toolsCount, int turnsCount) {
        return new AgentActivityEvent(
                "act-complete",
                "AI_ACTIVITY",
                conversationId,
                AgentActivityType.AGENT_COMPLETED,
                agentName,
                turnsCount, null,
                null,
                "🏁 " + agentName + " completed (" + toolsCount + " tool" + (toolsCount != 1 ? "s" : "") + " in " + turnsCount + " turn" + (turnsCount != 1 ? "s" : "") + " • " + totalDurationMs + "ms)",
                "COMPLETED",
                totalDurationMs,
                null,
                "Full agent reasoning cycle completed successfully",
                Map.of("totalDurationMs", totalDurationMs, "toolsCount", toolsCount, "turnsCount", turnsCount, "agent", agentName),
                Instant.now()
        );
    }
}
