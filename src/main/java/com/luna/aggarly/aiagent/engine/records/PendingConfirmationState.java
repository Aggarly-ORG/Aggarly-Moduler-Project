package com.luna.aggarly.aiagent.engine.records;

import java.util.List;
import java.util.Map;

/**
 * Stores the full agent state at the point a confirmation was required.
 * Persisted in Redis so the agent can resume from exactly where it stopped
 * when the user confirms the action.
 */
public record PendingConfirmationState(
        String toolName,
        Map<String, Object> arguments,
        String agentType,
        List<ChatMessage> conversationMessages
) {}
