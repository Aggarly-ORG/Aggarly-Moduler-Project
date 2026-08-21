package com.luna.aggarly.cleaning.exceptions;

import com.luna.aggarly.cleaning.entity.enums.CleaningStatus;
import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidCleaningStatusTransitionException extends AggarlyException {
    public InvalidCleaningStatusTransitionException(CleaningStatus from, CleaningStatus to) {
        super("Cannot transition cleaning task from " + from + " to " + to,
                HttpStatus.CONFLICT, "INVALID_CLEANING_STATUS_TRANSITION");
    }
}
