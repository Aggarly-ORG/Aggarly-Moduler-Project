package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PropertyAmenitiesParams(
        @NotNull
        @JsonPropertyDescription("The unique identifier (UUID) of the property.")
        UUID propertyId
) {}
