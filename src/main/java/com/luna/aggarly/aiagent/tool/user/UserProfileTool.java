package com.luna.aggarly.aiagent.tool.user;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserProfileTool implements Tool<UserProfileTool.Params, Map<String, Object>> {

    public record Params(
            @JsonPropertyDescription("UUID of the user whose profile is to be retrieved. Optional, defaults to current authenticated user.")
            UUID userId
    ) {}

    private final JsonSchemaService jsonSchemaService;

    @Override
    public String name() {
        return "user.profile";
    }

    @Override
    public String description() {
        return "Fetch the user profile details for a specified user ID.";
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
        UUID targetId = (params != null && params.userId() != null) ? params.userId() : (user != null ? user.getUserId() : null);
        return ToolResult.ok(Map.of(
                "userId", targetId != null ? targetId.toString() : "anonymous",
                "identityVerified", true,
                "preferredCurrency", "USD"
        ));
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(Params.class);
    }
}
