package com.luna.aggarly.availability.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailabilityCalendarResponse(
    UUID propertyId, 
    LocalDate from, 
    LocalDate to,
    List<AvailabilitySlotResponse> slots
) {}
