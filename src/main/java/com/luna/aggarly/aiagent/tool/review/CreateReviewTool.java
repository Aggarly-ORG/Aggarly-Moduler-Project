package com.luna.aggarly.aiagent.tool.review;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateReviewTool implements Tool<CreateReviewTool.Params, ReviewResponse> {

    public record Params(
            UUID propertyId,
            UUID bookingId,
            int rating,
            String comment,
            Integer cleanlinessRating,
            Integer accuracyRating,
            Integer checkInRating,
            Integer communicationRating,
            Integer locationRating,
            Integer valueRating
    ) {}

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
    public Class<Params> parameterType() {
        return Params.class;
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
    public ToolResult<ReviewResponse> execute(Params params, UserPrincipal user) {
        if (user == null || user.getUserId() == null) {
            return ToolResult.failed("AUTH_REQUIRED", "User must be authenticated to submit a review.");
        }
        CreateReviewRequest request = new CreateReviewRequest(
                params.propertyId(),
                params.bookingId(),
                params.rating(),
                params.comment(),
                params.cleanlinessRating(),
                params.accuracyRating(),
                params.checkInRating(),
                params.communicationRating(),
                params.locationRating(),
                params.valueRating()
        );
        ReviewResponse response = reviewService.createReview(request, user.getUserId());
        return ToolResult.ok(response);
    }
}
