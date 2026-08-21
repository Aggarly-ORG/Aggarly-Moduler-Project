package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GenerateReviewTool implements Tool<GenerateReviewTool.GenerateReviewRequest, String> {

    public record GenerateReviewRequest(
            UUID bookingId,
            List<String> keyPoints,
            int rating
    ) {}

    @Override
    public String name() {
        return "review.generateDraft";
    }

    @Override
    public String description() {
        return "Draft a natural, well-formatted guest review for a completed booking stay.";
    }

    @Override
    public Class<GenerateReviewRequest> parameterType() {
        return GenerateReviewRequest.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<String> execute(GenerateReviewRequest params, UserPrincipal user) {
        String draft = "Our stay was wonderful! " + String.join(". ", params.keyPoints()) + ". Highly recommended!";
        return ToolResult.ok(draft);
    }
}
