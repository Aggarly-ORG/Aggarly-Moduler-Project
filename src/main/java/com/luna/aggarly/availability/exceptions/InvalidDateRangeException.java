package com.luna.aggarly.availability.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends AggarlyException {
    public InvalidDateRangeException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
    }
}
