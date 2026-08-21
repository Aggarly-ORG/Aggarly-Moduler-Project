package com.luna.aggarly.property.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class PropertyNotFoundException extends AggarlyException {
    public PropertyNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND");
    }
}
