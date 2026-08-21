package com.luna.aggarly.aiagent.tool.user.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.UUID;

public record UserProfileParams(
        @JsonPropertyDescription("User UUID. If omitted, fetches the profile of the current authenticated user.")
        UUID userId
) {}
