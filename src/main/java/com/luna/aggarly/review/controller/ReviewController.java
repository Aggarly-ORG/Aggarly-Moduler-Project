package com.luna.aggarly.review.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.review.dto.CreateReviewRequest;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing property guest reviews, rating aggregation, and author deletion.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Property Reviews & Rating Summary APIs")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    @Operation(summary = "Submit a guest review for a completed stay", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(request, principal.getUserId());
        return ApiResponse.created(response, "Review submitted successfully").toResponseEntity();
    }

    @GetMapping("/properties/{propertyId}/reviews")
    @Operation(summary = "Get paginated reviews for a property (Public)")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getPropertyReviews(
            @PathVariable UUID propertyId,
            Pageable pageable) {
        Page<ReviewResponse> page = reviewService.getPropertyReviews(propertyId, pageable);
        return ApiResponse.paged(page, "Property reviews retrieved successfully").toResponseEntity();
    }

    @GetMapping("/properties/{propertyId}/rating-summary")
    @Operation(summary = "Get aggregated rating summary and score distribution for a property (Public)")
    public ResponseEntity<ApiResponse<PropertyRatingSummaryResponse>> getPropertyRatingSummary(
            @PathVariable UUID propertyId) {
        PropertyRatingSummaryResponse summary = reviewService.getPropertyRatingSummary(propertyId);
        return ApiResponse.ok(summary, "Rating summary retrieved successfully").toResponseEntity();
    }

    @GetMapping("/reviews/me")
    @Operation(summary = "Get all reviews authored by the current authenticated guest", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        Page<ReviewResponse> page = reviewService.getGuestReviews(principal.getUserId(), pageable);
        return ApiResponse.paged(page, "User reviews retrieved successfully").toResponseEntity();
    }

    @DeleteMapping("/reviews/{reviewId}")
    @Operation(summary = "Delete a review authored by the current guest", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reviewId) {
        reviewService.deleteReview(reviewId, principal.getUserId());
        return ApiResponse.<Void>empty("Review deleted successfully").toResponseEntity();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HOST')")
    @GetMapping("/reviews/host")
    @Operation(summary = "Get aggregated reviews across all sanctuaries for the host", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<com.luna.aggarly.review.dto.HostReviewItemDto>>> getHostReviews(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String filter) {
        List<com.luna.aggarly.review.dto.HostReviewItemDto> reviews = reviewService.getHostReviews(principal.getUserId(), filter);
        return ApiResponse.ok(reviews, "Host reviews retrieved successfully").toResponseEntity();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HOST')")
    @PostMapping("/reviews/{reviewId}/response")
    @Operation(summary = "Submit a public curator response to a guest review", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> replyToReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reviewId,
            @RequestBody com.luna.aggarly.review.dto.CuratorReviewReplyRequest request) {
        reviewService.respondToReview(reviewId, principal.getUserId(), request.response());
        return ApiResponse.<Void>empty("Curator response published successfully").toResponseEntity();
    }
}
