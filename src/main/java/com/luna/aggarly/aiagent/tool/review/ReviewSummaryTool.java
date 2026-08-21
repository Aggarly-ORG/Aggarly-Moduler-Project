package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReviewSummaryTool implements Tool<UUID, PropertyRatingSummaryResponse> {

    private final ReviewService reviewService;

    @Override
    public String name() {
        return "review.summary";
    }

    @Override
    public String description() {
        return "Summarize ratings and sentiment breakdown for a property based on past reviews.";
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
    public ToolResult<PropertyRatingSummaryResponse> execute(UUID propertyId, UserPrincipal user) {
        PropertyRatingSummaryResponse summary = reviewService.getPropertyRatingSummary(propertyId);
        return ToolResult.ok(summary);
    }
}
