package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.dto.request.PropertySearchRequest;
import com.luna.aggarly.property.dto.request.filters.CapacityFilter;
import com.luna.aggarly.property.dto.request.filters.LocationFilter;
import com.luna.aggarly.property.dto.request.filters.PricingFilter;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationTool implements Tool<RecommendationTool.Params, RecommendationTool.Response> {

    public record Params(
            @JsonPropertyDescription("Destination city, region, or area (e.g. 'Alexandria', 'Paris').")
            String destination,

            @JsonPropertyDescription("Number of guests requiring accommodation.")
            Integer guests,

            @JsonPropertyDescription("Target maximum nightly budget.")
            BigDecimal budget,

            @JsonPropertyDescription("Guest lifestyle intent or aesthetic preference (e.g. 'romantic', 'luxury', 'beachfront', 'workspace').")
            String lifestyle,

            @JsonPropertyDescription("Maximum number of recommendations to return (default 5).")
            Integer limit
    ) {}

    public record Response(
            List<Item> recommendations,
            int count,
            String lifestyleApplied,
            String summary
    ) {
        public record Item(
                UUID id,
                String title,
                String description,
                String propertyType,
                String city,
                String country,
                double basePrice,
                int maxGuests,
                double rating,
                int reviewCount,
                String coverPhotoUrl,
                List<String> keyHighlights
        ) {}
    }

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.recommendation";
    }

    @Override
    public String description() {
        return "Get top-rated property recommendations matching destination, guest count, and budget.";
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
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public ToolResult<Response> execute(Params params, UserPrincipal user) {
        log.info("Executing property.recommendation: destination={}, guests={}, budget={}, lifestyle={}",
                params.destination(), params.guests(), params.budget(), params.lifestyle());

        LocationFilter locationFilter = null;
        if (params.destination() != null && !params.destination().isBlank()) {
            locationFilter = new LocationFilter(params.destination().trim(), null, null, null, null, null);
        }

        PricingFilter pricingFilter = null;
        if (params.budget() != null) {
            pricingFilter = new PricingFilter(null, params.budget(), null);
        }

        CapacityFilter capacityFilter = null;
        if (params.guests() != null && params.guests() > 0) {
            capacityFilter = new CapacityFilter(params.guests(), null, null, null);
        }

        PropertySearchRequest searchRequest = new PropertySearchRequest(
                locationFilter,
                null,
                capacityFilter,
                pricingFilter,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        int limit = (params.limit() != null && params.limit() > 0) ? Math.min(params.limit(), 20) : 5;
        Sort sort = Sort.by(Sort.Direction.DESC, "avgRating").and(Sort.by(Sort.Direction.DESC, "reviewCount"));
        Slice<PropertyResponse> page = propertyService.searchProperties(searchRequest, PageRequest.of(0, limit, sort));

        List<Response.Item> items = page.getContent().stream()
                .map(this::mapToItem)
                .toList();

        String summary = String.format("Found %d top-rated recommendations for %s.",
                items.size(), params.destination() != null ? params.destination() : "your destination");

        Response response = new Response(
                items,
                items.size(),
                params.lifestyle() != null ? params.lifestyle() : "GENERAL",
                summary
        );

        return ToolResult.ok(response);
    }

    private Response.Item mapToItem(PropertyResponse p) {
        String cover = (p.images() != null && !p.images().isEmpty())
                ? p.images().stream().filter(PropertyImageResponse::isCover).findFirst().map(PropertyImageResponse::objectKey).orElse(p.images().get(0).objectKey())
                : null;

        List<String> highlights = (p.amenities() != null)
                ? p.amenities().stream().limit(4).map(com.luna.aggarly.property.dto.response.AmenityResponse::name).toList()
                : List.of();

        return new Response.Item(
                p.id(),
                p.title(),
                p.description(),
                p.propertyType() != null ? p.propertyType().name() : null,
                p.address() != null ? p.address().city() : null,
                p.address() != null ? p.address().country() : null,
                p.basePricePerNight() != null ? p.basePricePerNight().doubleValue() : 0.0,
                p.maxGuests(),
                p.avgRating() != null ? p.avgRating().doubleValue() : 0.0,
                p.reviewCount(),
                cover,
                highlights
        );
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(Response.class);
    }
}
