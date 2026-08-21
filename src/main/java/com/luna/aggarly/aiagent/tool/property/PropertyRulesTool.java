package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.PropertyRulesParams;
import com.luna.aggarly.aiagent.tool.property.record.PropertyRulesResponse;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyRulesTool implements Tool<PropertyRulesParams, PropertyRulesResponse> {

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.rules";
    }

    @Override
    public String description() {
        return "Get house rules, check-in and check-out times, guest capacity limits, and cancellation policy for a property.";
    }

    @Override
    public Class<PropertyRulesParams> parameterType() {
        return PropertyRulesParams.class;
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
    public ToolResult<PropertyRulesResponse> execute(PropertyRulesParams params, UserPrincipal user) {
        log.info("Executing property.rules for propertyId={}", params.propertyId());

        try {
            PropertyResponse property = propertyService.getPropertyById(params.propertyId());

            List<String> rules = new ArrayList<>();
            rules.add("No smoking inside the property");
            rules.add("No parties or loud events");
            rules.add("Quiet hours between 10:00 PM and 8:00 AM");
            rules.add("Maximum capacity of " + property.maxGuests() + " guests must be respected");

            PropertyRulesResponse response = new PropertyRulesResponse(
                    params.propertyId(),
                    property.title(),
                    property.maxGuests(),
                    property.bedrooms(),
                    property.bathrooms(),
                    property.cancellationPolicy() != null ? property.cancellationPolicy().name() : "FLEXIBLE",
                    "3:00 PM - 10:00 PM",
                    "11:00 AM",
                    true,
                    false,
                    rules
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Failed to fetch property rules for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("PROPERTY_NOT_FOUND", "Property not found with ID: " + params.propertyId());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(PropertyRulesParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyRulesResponse.class);
    }
}
