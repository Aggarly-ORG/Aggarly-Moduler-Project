package com.luna.aggarly.aiagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.aiagent.schema.JsonSchemaService;
import com.luna.aggarly.user.security.UserPrincipal;

public interface Tool<TParams, TResult> {
    String name();
    String description();
    Class<TParams> parameterType();

    boolean requiresConfirmation();

    /**
     * Whether this tool requires an authenticated (non-null) user.
     * Booking tools that access user-specific data should override to return true.
     */
    default boolean requiresAuthentication() {
        return false;
    }

    ToolResult<TResult> execute(TParams params, UserPrincipal currentUser);

    default JsonNode parameterSchema() {
        return new JsonSchemaService().generate(parameterType());
    }

    default JsonNode responseSchema() {
        return new JsonSchemaService().generate(Void.class);
    }
}
