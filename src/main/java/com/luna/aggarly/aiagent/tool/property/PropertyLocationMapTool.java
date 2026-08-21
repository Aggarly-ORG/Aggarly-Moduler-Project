package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyLocationMapParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyLocationMapResponse;
import com.luna.aggarly.property.dto.response.AddressResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyLocationMapTool implements Tool<PropertyLocationMapParams, PropertyLocationMapResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.locationMap";
    }

    @Override
    public String description() {
        return "Fetch the geographic address, city, country, postal code, and map coordinates for a property.";
    }

    @Override
    public Class<PropertyLocationMapParams> parameterType() {
        return PropertyLocationMapParams.class;
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
    public ToolResult<PropertyLocationMapResponse> execute(PropertyLocationMapParams params, UserPrincipal user) {
        log.info("Executing property.locationMap for propertyId={}", params.propertyId());

        try {
            PropertyResponse property = propertyService.getPropertyById(params.propertyId());
            AddressResponse addr = property.address();

            String street = addr != null ? addr.street() : null;
            String city = addr != null ? addr.city() : null;
            String state = addr != null ? addr.state() : null;
            String country = addr != null ? addr.country() : null;
            String postal = addr != null ? addr.zipCode() : null;
            Double lat = property.latitude() != null ? property.latitude().doubleValue() : null;
            Double lng = property.longitude() != null ? property.longitude().doubleValue() : null;

            String mapUrl = (lat != null && lng != null)
                    ? "https://maps.google.com/?q=" + lat + "," + lng
                    : null;

            PropertyLocationMapResponse response = new PropertyLocationMapResponse(
                    params.propertyId(),
                    street,
                    city,
                    state,
                    country,
                    postal,
                    lat,
                    lng,
                    mapUrl
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to fetch location for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("LOCATION_ERROR", "Could not load location: " + ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyLocationMapParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyLocationMapResponse.class);
    }
}
