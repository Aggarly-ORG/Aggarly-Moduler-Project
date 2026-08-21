package com.luna.aggarly.availability.dto;

import java.time.LocalDate;

public record BlockDatesRequest(
    LocalDate checkIn, 
    LocalDate checkOut, 
    String reason
) {}
