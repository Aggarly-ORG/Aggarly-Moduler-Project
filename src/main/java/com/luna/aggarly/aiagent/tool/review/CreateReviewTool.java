package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateReviewTool implements Tool<CreateReviewRequest, ReviewResponse> {

    private final ReviewService reviewService;

    @Override
    public String name() {
        return "review.create";
    }

    @Override
    public String description() {
        return "Submit a property review and ratings for a completed stay.";
    }

    @Override
    public Class<CreateReviewRequest> parameterType() {
        return CreateReviewRequest.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ToolResult<ReviewResponse> execute(CreateReviewRequest params, UserPrincipal user) {
        if (user == null || user.getUserId() == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to submit a review.");
        }
        ReviewResponse response = reviewService.createReview(params, user.getUserId());
        return ToolResult.ok(response);
    }
}
