package com.luna.aggarly.availability.utils;

import com.luna.aggarly.availability.exceptions.InvalidDateRangeException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DateRangeUtils {

    private DateRangeUtils() {}

    public static void validate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new InvalidDateRangeException("Check-in and check-out dates are required");
        }
        if (!checkIn.isBefore(checkOut)) {
            throw new InvalidDateRangeException("Check-out must be after check-in");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new InvalidDateRangeException("Check-in cannot be in the past");
        }
    }

    public static long nights(LocalDate checkIn, LocalDate checkOut) {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
