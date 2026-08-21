package com.luna.aggarly.aiagent.tool.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.property.record.AvailabilityCheckParams;
import com.luna.aggarly.aiagent.tool.property.record.AvailabilityCheckToolResponse;
import com.luna.aggarly.availability.dto.AvailabilityCheckResponse;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityTool implements Tool<AvailabilityCheckParams, AvailabilityCheckToolResponse> {

    private final AvailabilityService availabilityService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "property.availability";
    }

    @Override
    public String description() {
        return "Check whether a specific property is available for booking during a given date range (checkIn to checkOut). it's used to make sure that the range is available.";
    }

    @Override
    public Class<AvailabilityCheckParams> parameterType() {
        return AvailabilityCheckParams.class;
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
    public ToolResult<AvailabilityCheckToolResponse> execute(AvailabilityCheckParams params, UserPrincipal user) {
        log.info("Executing property.availability for propertyId={}, dates={} to {}",
                params.propertyId(), params.checkIn(), params.checkOut());

        try {
            AvailabilityCheckResponse result = availabilityService.checkAvailability(
                    params.propertyId(), params.checkIn(), params.checkOut()
            );

            boolean isAvailable = result != null && result.available();
            String message = isAvailable
                    ? "Property is available for the requested dates."
                    : "Property is not available for the requested dates.";

            AvailabilityCheckToolResponse response = new AvailabilityCheckToolResponse(
                    params.propertyId(),
                    isAvailable,
                    params.checkIn(),
                    params.checkOut(),
                    message
            );

            return ToolResult.ok(response);
        } catch (Exception ex) {
            log.error("Availability check failed for propertyId={}", params.propertyId(), ex);
            return ToolResult.failed("AVAILABILITY_CHECK_FAILED", "Could not check availability: " + ex.getMessage());
        }
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(AvailabilityCheckParams.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(AvailabilityCheckToolResponse.class);
    }
}
