package com.luna.aggarly.property.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UnauthorizedPropertyAccessException extends AggarlyException {
    public UnauthorizedPropertyAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_PROPERTY_ACCESS");
    }
}
