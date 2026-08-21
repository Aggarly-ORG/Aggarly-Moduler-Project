package com.luna.aggarly.aiagent.tool.property.record;

import java.util.List;
import java.util.UUID;

public record PropertyRulesResponse(
        UUID propertyId,
        String propertyTitle,
        int maxGuests,
        int bedrooms,
        int bathrooms,
        String cancellationPolicy,
        String checkInTime,
        String checkOutTime,
        boolean selfCheckIn,
        boolean petsAllowed,
        List<String> houseRules
) {}
