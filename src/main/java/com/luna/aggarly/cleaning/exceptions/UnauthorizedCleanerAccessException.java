package com.luna.aggarly.cleaning.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UnauthorizedCleanerAccessException extends AggarlyException {
    public UnauthorizedCleanerAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_CLEANING_ACCESS");
    }
}
