package com.luna.aggarly.aiagent.tool.property.record;

import com.luna.aggarly.availability.dto.AvailabilitySlotResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PropertyCalendarToolResponse(
        UUID propertyId,
        LocalDate from,
        LocalDate to,
        List<AvailabilitySlotResponse> slots,
        int totalBlockedSlots,
        String summary
) {}
