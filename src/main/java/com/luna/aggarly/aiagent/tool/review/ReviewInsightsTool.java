package com.luna.aggarly.aiagent.tool.review;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.review.dto.PropertyRatingSummaryResponse;
import com.luna.aggarly.review.dto.ReviewResponse;
import com.luna.aggarly.review.service.ReviewService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewInsightsTool implements Tool<ReviewInsightsTool.Params, Map<String, Object>> {

    public record Params(
            @JsonPropertyDescription("The UUID of the property to analyze reviews for.")
            UUID propertyId,

            @JsonPropertyDescription("Optional maximum number of recent reviews to analyze (1-100, default 50).")
            Integer sampleSize
    ) {}

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "was", "were", "a", "an", "to", "of", "in", "it", "is", "for", "on", "with",
            "very", "had", "has", "have", "this", "that", "but", "not", "you", "we", "our", "my", "i",
            "at", "as", "be", "been", "so", "there", "their", "they", "would", "could", "us", "stay");

    private final ReviewService reviewService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "review.insights";
    }

    @Override
    public String description() {
        return "Complete guest feedback insights for a property: average rating, category sub-ratings " +
                "(cleanliness, accuracy, check-in, communication, location, value), rating distribution, " +
                "derived guest sentiment with top keywords, and recent review comments.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        try {
            if (params == null || params.propertyId() == null) {
                return ToolResult.failed("INVALID_ARGUMENTS", "propertyId is required");
            }
            PropertyRatingSummaryResponse summary = reviewService.getPropertyRatingSummary(params.propertyId());
            int sampleSize = params.sampleSize() != null ? Math.min(Math.max(params.sampleSize(), 1), 100) : 50;
            Pageable pageable = PageRequest.of(0, sampleSize);
            List<ReviewResponse> recent = reviewService.getPropertyReviews(params.propertyId(), pageable).getContent();

            Map<String, Object> insights = new LinkedHashMap<>();
            insights.put("propertyId", params.propertyId());
            insights.put("averageRating", summary.avgRating());
            insights.put("totalReviews", summary.totalReviews());
            insights.put("categoryAverages", computeCategoryAverages(recent));
            insights.put("ratingDistribution", computeRatingDistribution(recent));
            insights.put("sentiment", deriveSentiment(summary, recent));
            insights.put("recentComments", recent.stream()
                    .filter(r -> r.comment() != null && !r.comment().isBlank())
                    .limit(5)
                    .map(ReviewResponse::comment)
                    .toList());
            return ToolResult.ok(insights);
        } catch (Exception ex) {
            log.error("Failed to build review insights for property {}", params != null ? params.propertyId() : null, ex);
            return ToolResult.failed("REVIEW_INSIGHTS_FAILED", ex.getMessage());
        }
    }

    private Map<String, Object> computeCategoryAverages(List<ReviewResponse> reviews) {
        Map<String, double[]> sums = new LinkedHashMap<>();
        collectCategory(sums, "cleanliness", reviews.stream().map(ReviewResponse::cleanlinessRating));
        collectCategory(sums, "accuracy", reviews.stream().map(ReviewResponse::accuracyRating));
        collectCategory(sums, "checkIn", reviews.stream().map(ReviewResponse::checkInRating));
        collectCategory(sums, "communication", reviews.stream().map(ReviewResponse::communicationRating));
        collectCategory(sums, "location", reviews.stream().map(ReviewResponse::locationRating));
        collectCategory(sums, "value", reviews.stream().map(ReviewResponse::valueRating));

        Map<String, Object> averages = new LinkedHashMap<>();
        sums.forEach((key, agg) -> {
            if (agg[1] > 0) {
                averages.put(key, Math.round((agg[0] / agg[1]) * 100.0) / 100.0);
            }
        });
        return averages;
    }

    private void collectCategory(Map<String, double[]> sums, String key, java.util.stream.Stream<Integer> values) {
        values.filter(Objects::nonNull).forEach(v -> {
            double[] agg = sums.computeIfAbsent(key, k -> new double[2]);
            agg[0] += v;
            agg[1] += 1;
        });
    }

    private Map<Integer, Long> computeRatingDistribution(List<ReviewResponse> reviews) {
        Map<Integer, Long> distribution = reviews.stream()
                .collect(Collectors.groupingBy(ReviewResponse::rating, Collectors.counting()));
        Map<Integer, Long> full = new TreeMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            full.put(stars, distribution.getOrDefault(stars, 0L));
        }
        return full;
    }

    private Map<String, Object> deriveSentiment(PropertyRatingSummaryResponse summary, List<ReviewResponse> reviews) {
        Map<String, Object> sentiment = new LinkedHashMap<>();
        if (reviews.isEmpty()) {
            sentiment.put("label", "NO_DATA");
            sentiment.put("topKeywords", List.of());
            return sentiment;
        }

        double avg = summary.avgRating() != null ? summary.avgRating().doubleValue() : 0.0;
        String label = avg >= 4.5 ? "OVERWHELMINGLY_POSITIVE"
                : avg >= 3.8 ? "POSITIVE"
                : avg >= 3.0 ? "MIXED"
                : avg >= 2.0 ? "NEGATIVE" : "VERY_NEGATIVE";
        sentiment.put("label", label);
        sentiment.put("score", avg);

        Map<String, Integer> keywordCounts = new HashMap<>();
        for (ReviewResponse review : reviews) {
            if (review.comment() == null || review.comment().isBlank()) continue;
            for (String token : review.comment().toLowerCase().split("[^a-zA-Z]+")) {
                if (token.length() < 4 || STOP_WORDS.contains(token)) continue;
                keywordCounts.merge(token, 1, Integer::sum);
            }
        }
        sentiment.put("topKeywords", keywordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(Map.Entry::getKey)
                .toList());
        return sentiment;
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
