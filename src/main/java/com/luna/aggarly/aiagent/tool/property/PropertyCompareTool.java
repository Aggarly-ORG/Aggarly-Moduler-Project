package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyCompareParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyCompareResponse;
import com.luna.aggarly.aiagent.tool.property.record.PropertyComparisonItem;
import com.luna.aggarly.property.dto.response.AmenityResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyCompareTool implements Tool<PropertyCompareParams, PropertyCompareResponse> {

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
    public Class<PropertyCompareParams> parameterType() {
        return PropertyCompareParams.class;
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
    public ToolResult<PropertyCompareResponse> execute(PropertyCompareParams params, UserPrincipal user) {
        log.info("Executing property.compare for propertyIds={}", params.propertyIds());

        if (params.propertyIds() == null || params.propertyIds().isEmpty()) {
            return ToolResult.failed("EMPTY_PROPERTY_LIST", "Please provide at least 2 property IDs to compare.");
        }

        List<PropertyComparisonItem> items = new ArrayList<>();
        for (UUID id : params.propertyIds()) {
            try {
                PropertyResponse p = propertyService.getPropertyById(id);
                List<String> amenities = p.amenities() != null
                        ? p.amenities().stream().map(AmenityResponse::name).toList()
                        : List.of();

                items.add(new PropertyComparisonItem(
                        p.id(),
                        p.title(),
                        p.address() != null ? p.address().city() : null,
                        p.address() != null ? p.address().country() : null,
                        p.propertyType() != null ? p.propertyType().name() : null,
                        p.maxGuests(),
                        p.bedrooms(),
                        p.bathrooms(),
                        p.basePricePerNight(),
                        p.avgRating(),
                        p.reviewCount(),
                        p.cancellationPolicy() != null ? p.cancellationPolicy().name() : null,
                        amenities
                ));
            } catch (Exception ex) {
                log.warn("Property with ID {} could not be loaded for comparison", id);
            }
        }

        return ToolResult.ok(new PropertyCompareResponse(items.size(), items));
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyCompareParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyCompareResponse.class);
    }
}
