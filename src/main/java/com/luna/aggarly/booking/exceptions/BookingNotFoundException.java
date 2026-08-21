package com.luna.aggarly.booking.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class BookingNotFoundException extends AggarlyException {
    public BookingNotFoundException(UUID id) {
        super("Booking not found: " + id, HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
    }
}
