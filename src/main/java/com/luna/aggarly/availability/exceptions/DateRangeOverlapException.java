package com.luna.aggarly.availability.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class DateRangeOverlapException extends AggarlyException {
    public DateRangeOverlapException(String message) {
        super(message, HttpStatus.CONFLICT, "DATE_RANGE_OVERLAP");
    }
}
