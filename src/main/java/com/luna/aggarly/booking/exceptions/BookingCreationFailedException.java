package com.luna.aggarly.booking.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class BookingCreationFailedException extends AggarlyException {
    public BookingCreationFailedException(String message) {
        super(message, HttpStatus.CONFLICT, "BOOKING_CREATION_FAILED");
    }
}
