package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyCompareTool implements Tool<PropertyCompareTool.Params, PropertyCompareTool.Response> {

    public record Params(
            @NotEmpty(message = "Property IDs list cannot be empty")
            @Size(min = 2, max = 5, message = "Please provide between 2 and 5 property IDs to compare")
            @JsonPropertyDescription("List of 2 to 5 property UUIDs to compare side-by-side.")
            List<UUID> propertyIds
    ) {}

    public record Response(
            List<Item> properties,
            int count,
            String comparisonSummary
    ) {
        public record Item(
                UUID id,
                String title,
                String propertyType,
                String city,
                String country,
                double pricePerNight,
                int maxGuests,
                int bedrooms,
                int bathrooms,
                double rating,
                int reviewCount,
                List<String> amenities,
                String houseRules,
                String checkInTime,
                String checkOutTime,
                String cancellationPolicy,
                String coverPhotoUrl
        ) {}
    }

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.compare";
    }

    @Override
    public String description() {
        return "Fetch side-by-side comparative details for 2 to 5 property UUIDs (price, capacity, rating, amenities, rules).";
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
        log.info("Executing property.compare for propertyIds={}", params.propertyIds());

        if (params.propertyIds() == null || params.propertyIds().isEmpty()) {
            return ToolResult.failed("EMPTY_PROPERTY_LIST", "Please provide at least 2 property IDs to compare.");
        }

        List<Response.Item> items = new ArrayList<>();
        for (UUID propertyId : params.propertyIds()) {
            try {
                PropertyResponse p = propertyService.getPropertyById(propertyId);
                List<String> amenityNames = p.amenities() != null
                        ? p.amenities().stream().map(AmenityResponse::name).toList()
                        : List.of();

                String coverPhoto = (p.images() != null && !p.images().isEmpty())
                        ? p.images().stream().filter(PropertyImageResponse::isCover).findFirst().map(PropertyImageResponse::objectKey).orElse(p.images().get(0).objectKey())
                        : null;

                items.add(new Response.Item(
                        p.id(),
                        p.title(),
                        p.propertyType() != null ? p.propertyType().name() : null,
                        p.address() != null ? p.address().city() : null,
                        p.address() != null ? p.address().country() : null,
                        p.basePricePerNight() != null ? p.basePricePerNight().doubleValue() : 0.0,
                        p.maxGuests(),
                        p.bedrooms(),
                        p.bathrooms(),
                        p.avgRating() != null ? p.avgRating().doubleValue() : 0.0,
                        p.reviewCount(),
                        amenityNames,
                        "Standard house rules apply.",
                        "15:00",
                        "11:00",
                        p.cancellationPolicy() != null ? p.cancellationPolicy().name() : "MODERATE",
                        coverPhoto
                ));
            } catch (Exception ex) {
                log.warn("Failed to load property {} for comparison: {}", propertyId, ex.getMessage());
            }
        }

        if (items.isEmpty()) {
            return ToolResult.failed("PROPERTIES_NOT_FOUND", "None of the specified property IDs could be found.");
        }

        String summary = String.format("Successfully loaded comparison data for %d properties.", items.size());
        return ToolResult.ok(new Response(items, items.size(), summary));
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
