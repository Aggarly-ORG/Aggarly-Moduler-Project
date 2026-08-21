package com.luna.aggarly.booking.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidBookingStateException extends AggarlyException {
    public InvalidBookingStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_BOOKING_STATE");
    }
}
