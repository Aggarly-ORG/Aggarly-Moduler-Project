package com.luna.aggarly.availability.dto;

import java.time.LocalDate;
import java.util.List;

public record BulkAvailabilityUpdateRequest(List<Range> ranges) {
    public record Range(LocalDate checkIn, LocalDate checkOut, boolean available) {}
}
