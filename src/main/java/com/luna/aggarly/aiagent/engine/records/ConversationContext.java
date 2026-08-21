package com.luna.aggarly.aiagent.engine.records;

import com.luna.aggarly.aiagent.entity.AiSearchContext;
import com.luna.aggarly.aiagent.entity.AiUserMemory;

import java.util.List;

public record ConversationContext(
        AiSearchContext activeSearchContext,
        List<AiUserMemory> userMemories,
        List<ChatMessage> conversationHistory
) {
    public ConversationContext(AiSearchContext activeSearchContext, List<AiUserMemory> userMemories) {
        this(activeSearchContext, userMemories, List.of());
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
