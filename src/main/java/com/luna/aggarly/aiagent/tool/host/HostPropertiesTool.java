package com.luna.aggarly.aiagent.tool.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.service.PropertyService;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Tool for retrieving properties owned by the authenticated host.
 */
@Component
@RequiredArgsConstructor
public class HostPropertiesTool implements Tool<HostPropertiesTool.Params, List<PropertyResponse>> {

    public record Params(
            UUID hostId
    ) {}

    private final PropertyService propertyService;
    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "host.properties";
    }

    @Override
    public String description() {
        return "Retrieve the list of properties owned and managed by the current host.";
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }

    @Override
    public JsonNode responseSchema() {
        return jsonSchemaService.generate(PropertyResponse.class);
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<List<PropertyResponse>> execute(Params params, UserPrincipal user) {
        UUID targetHostId = (params != null && params.hostId() != null)
                ? params.hostId()
                : (user != null ? user.getUserId() : null);
        if (targetHostId == null) {
            return ToolResult.failed("UNAUTHENTICATED", "Host identity is required");
        }
        Page<PropertyResponse> properties = propertyService.getHostProperties(targetHostId, PageRequest.of(0, 20));
        return ToolResult.ok(properties.getContent());
    }
}
