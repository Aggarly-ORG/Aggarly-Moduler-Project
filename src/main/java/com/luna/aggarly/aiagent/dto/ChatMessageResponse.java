package com.luna.aggarly.aiagent.dto;

import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID conversationId,
        String content,
        List<String> toolCalls,

        /** Present when the agent is waiting for the user to confirm a sensitive action. */
        boolean requiresConfirmation,

        /** Token the client must send to POST /api/v1/ai/confirm/{token} to resume the agent. */
        String confirmationToken,

        /** Human-readable name of the tool pending confirmation. */
        String pendingToolName,

        /** Optional structured JSON payload for rich UI cards (PropertyDetails, Comparison, Calendar, Chef, Timeline, etc.). */
        String metadataJson
) {
    /** Convenience constructor for normal (non-confirmation) responses without metadata. */
    public ChatMessageResponse(UUID conversationId, String content, List<String> toolCalls) {
        this(conversationId, content, toolCalls, false, null, null, null);
    }

    /** Convenience constructor for confirmation responses without metadata. */
    public ChatMessageResponse(UUID conversationId, String content, List<String> toolCalls, boolean requiresConfirmation, String confirmationToken, String pendingToolName) {
        this(conversationId, content, toolCalls, requiresConfirmation, confirmationToken, pendingToolName, null);
    }
}
