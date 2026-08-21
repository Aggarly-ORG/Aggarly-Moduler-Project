package com.luna.aggarly.aiagent.tool.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.aiagent.tool.user.record.UserProfileParams;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserProfileTool implements Tool<UserProfileParams, Map<String, Object>> {

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
    public Class<UserProfileParams> parameterType() {
        return UserProfileParams.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(UserProfileParams params, UserPrincipal user) {
        UUID targetId = (params != null && params.userId() != null) ? params.userId() : (user != null ? user.getUserId() : null);
        return ToolResult.ok(Map.of(
                "userId", targetId != null ? targetId.toString() : "anonymous",
                "identityVerified", true,
                "preferredCurrency", "USD"
        ));
    }

    @Override
    public JsonNode parameterSchema() {
        return jsonSchemaService.generate(UserProfileParams.class);
    }
}
