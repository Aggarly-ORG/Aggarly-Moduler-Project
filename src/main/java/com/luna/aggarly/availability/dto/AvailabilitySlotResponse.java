package com.luna.aggarly.availability.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilitySlotResponse(
    UUID id, 
    UUID propertyId, 
    LocalDate startDate,
    LocalDate endDate, 
    boolean available, 
    String blockReason
) {}
