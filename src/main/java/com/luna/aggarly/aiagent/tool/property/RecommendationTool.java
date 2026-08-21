package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyRecommendationParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyRecommendationResponse;
import com.luna.aggarly.aiagent.tool.property.record.PropertySummaryItem;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationTool implements Tool<PropertyRecommendationParams, PropertyRecommendationResponse> {

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
    public Class<PropertyRecommendationParams> parameterType() {
        return PropertyRecommendationParams.class;
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
    public ToolResult<PropertyRecommendationResponse> execute(PropertyRecommendationParams params, UserPrincipal user) {
        log.info("Executing property.recommendation: destination={}, guests={}, maxPrice={}",
                params.destination(), params.guests(), params.maxPrice());

        LocationFilter locationFilter = params.destination() != null
                ? new LocationFilter(params.destination(), null, null, null, null, null)
                : null;

        CapacityFilter capacityFilter = params.guests() != null
                ? new CapacityFilter(params.guests(), null, null, null)
                : null;

        PricingFilter pricingFilter = params.maxPrice() != null
                ? new PricingFilter(null, params.maxPrice(), null)
                : null;

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

        Page<PropertyResponse> page = propertyService.searchProperties(
                searchRequest,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "avgRating"))
        );

        List<PropertySummaryItem> items = page.getContent().stream()
                .map(this::toSummaryItem)
                .toList();

        String reason = "Top " + items.size() + " highly rated properties"
                + (params.destination() != null ? " in " + params.destination() : "")
                + " matching your travel preferences.";

        return ToolResult.ok(new PropertyRecommendationResponse(items.size(), reason, items));
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyRecommendationParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyRecommendationResponse.class);
    }

    private PropertySummaryItem toSummaryItem(PropertyResponse p) {
        String city = p.address() != null ? p.address().city() : null;
        String country = p.address() != null ? p.address().country() : null;
        String coverImage = null;

        if (p.images() != null && !p.images().isEmpty()) {
            coverImage = p.images().stream()
                    .filter(PropertyImageResponse::isCover)
                    .findFirst()
                    .map(PropertyImageResponse::objectKey)
                    .orElse(p.images().get(0).objectKey());
        }

        List<String> allImages = (p.images() != null && !p.images().isEmpty())
                ? p.images().stream().map(img -> formatImageUrl(img.objectKey())).toList()
                : List.of();

        String formattedCover = formatImageUrl(coverImage);

        return new PropertySummaryItem(
                p.id(),
                p.title(),
                city,
                country,
                p.propertyType() != null ? p.propertyType().name() : null,
                p.maxGuests(),
                p.bedrooms(),
                p.bathrooms(),
                p.basePricePerNight(),
                p.avgRating(),
                p.reviewCount(),
                formattedCover,
                formattedCover,
                allImages
        );
    }

    private String formatImageUrl(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("http://") || key.startsWith("https://")) return key;
        return "http://localhost:8081/api/v1/storage/files/view?key=" + key;
    }
}
