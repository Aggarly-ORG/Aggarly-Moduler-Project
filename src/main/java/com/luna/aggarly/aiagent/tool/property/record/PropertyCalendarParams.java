package com.luna.aggarly.aiagent.tool.property.record;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PropertyCalendarParams(
        @NotNull(message = "Property ID is required.")
        @JsonPropertyDescription("The UUID of the property whose calendar to retrieve.")
        UUID propertyId,

        @JsonPropertyDescription("Optional start date for the calendar view (defaults to today).")
        LocalDate from,

        @JsonPropertyDescription("Optional end date for the calendar view (defaults to 3 months from today).")
        LocalDate to
) {}
