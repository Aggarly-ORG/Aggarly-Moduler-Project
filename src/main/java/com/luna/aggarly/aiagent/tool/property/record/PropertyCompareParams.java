package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PropertyCompareParams(
        @NotNull
        @Size(min = 2, max = 5)
        @JsonPropertyDescription("List of 2 to 5 property UUIDs to compare side-by-side.")
        List<UUID> propertyIds
) {}
