package com.luna.aggarly.aiagent.engine.records;

import com.luna.aggarly.aiagent.engine.enums.IntentCategory;

public record ClassifiedIntent(
        IntentCategory category,
        String rawMessage,
        ConversationContext context
) {
}
