package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SentimentAnalysisTool implements Tool<UUID, Map<String, Object>> {

    @Override
    public String name() {
        return "review.sentiment";
    }

    @Override
    public String description() {
        return "Analyze overall guest sentiment (positive, neutral, negative) and common keywords in property reviews.";
    }

    @Override
    public Class<UUID> parameterType() {
        return UUID.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(UUID propertyId, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "overallSentiment", "OVERWHELMINGLY_POSITIVE",
                "score", 4.85,
                "topKeywords", java.util.List.of("clean", "great location", "responsive host", "quiet")
        ));
    }
}
