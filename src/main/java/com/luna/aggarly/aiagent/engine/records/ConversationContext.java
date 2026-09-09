package com.luna.aggarly.aiagent.engine.records;

import com.luna.aggarly.aiagent.entity.AiSearchContext;
import com.luna.aggarly.aiagent.entity.AiUserMemory;

import java.util.List;
import java.util.UUID;

public record ConversationContext(
        UUID conversationId,
        AiSearchContext activeSearchContext,
        List<AiUserMemory> userMemories,
        List<ChatMessage> conversationHistory,
        String screenshotUrl
) {
    public ConversationContext(UUID conversationId, AiSearchContext activeSearchContext, List<AiUserMemory> userMemories, List<ChatMessage> conversationHistory) {
        this(conversationId, activeSearchContext, userMemories, conversationHistory, null);
    }

    public ConversationContext(AiSearchContext activeSearchContext, List<AiUserMemory> userMemories) {
        this(null, activeSearchContext, userMemories, List.of(), null);
    }

    public ConversationContext(AiSearchContext activeSearchContext, List<AiUserMemory> userMemories, List<ChatMessage> conversationHistory) {
        this(null, activeSearchContext, userMemories, conversationHistory, null);
    }

    public String activeFilters() {
        return activeSearchContext != null ? activeSearchContext.getFiltersJson() : "{}";
    }

    public String formatUserMemoriesBlock() {
        if (userMemories == null || userMemories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n================================================================");
        sb.append("\nUSER PROFILE & CONFIRMED PREFERENCES (LONG-TERM MEMORY)");
        sb.append("\n================================================================");
        sb.append("\nThe following long-term preferences and facts have been confirmed by the user. Always tailor your responses, recommendations, filters, amenities, and tone to respect these preferences unless the user explicitly requests otherwise in their current message:\n");
        for (AiUserMemory memory : userMemories) {
            sb.append("• ").append(memory.getMemoryKey()).append(": ").append(memory.getMemoryValue()).append("\n");
        }
        return sb.toString();
    }
}
