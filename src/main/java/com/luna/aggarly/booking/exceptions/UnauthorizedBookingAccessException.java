package com.luna.aggarly.booking.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UnauthorizedBookingAccessException extends AggarlyException {
    public UnauthorizedBookingAccessException() {
        super("You do not have access to this booking", HttpStatus.FORBIDDEN, "UNAUTHORIZED_BOOKING_ACCESS");
    }
}
