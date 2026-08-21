package com.luna.aggarly.aiagent.tool.property.record;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityCheckToolResponse(
        UUID propertyId,
        boolean isAvailable,
        LocalDate checkIn,
        LocalDate checkOut,
        String message
) {}
