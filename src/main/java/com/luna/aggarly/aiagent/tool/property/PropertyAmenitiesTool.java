package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.AmenityItem;
import com.luna.aggarly.aiagent.tool.property.record.PropertyAmenitiesParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyAmenitiesResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyAmenitiesTool implements Tool<PropertyAmenitiesParams, PropertyAmenitiesResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.amenities";
    }

    @Override
    public String description() {
        return "Fetch the complete list of amenities (Wi-Fi, swimming pool, kitchen, workspace, heating, AC, etc.) for a property.";
    }

    @Override
    public Class<PropertyAmenitiesParams> parameterType() {
        return PropertyAmenitiesParams.class;
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
    public ToolResult<PropertyAmenitiesResponse> execute(PropertyAmenitiesParams params, UserPrincipal user) {
        log.info("Executing property.amenities for propertyId={}", params.propertyId());

        try {
            PropertyResponse property = propertyService.getPropertyById(params.propertyId());

            List<AmenityItem> items = property.amenities() != null
                    ? property.amenities().stream()
                    .map(a -> new AmenityItem(
                            a.id(),
                            a.name(),
                            a.category() != null ? a.category().name() : null,
                            a.icon()
                    ))
                    .toList()
                    : List.of();

            PropertyAmenitiesResponse response = new PropertyAmenitiesResponse(
                    params.propertyId(),
                    items.size(),
                    items
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to fetch amenities for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("PROPERTY_NOT_FOUND", "Property not found with ID: " + params.propertyId());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyAmenitiesParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyAmenitiesResponse.class);
    }
}
