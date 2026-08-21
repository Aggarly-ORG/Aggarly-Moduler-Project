package com.luna.aggarly.availability.dto;

import java.time.LocalDate;

public record AvailabilityCheckResponse(
    boolean available, 
    LocalDate checkIn, 
    LocalDate checkOut, 
    String reason
) {}
