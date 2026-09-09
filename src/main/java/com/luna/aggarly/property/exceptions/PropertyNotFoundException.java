package com.luna.aggarly.property.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PropertyNotFoundException extends AggarlyException {
    public PropertyNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND");
    }

    public PropertyNotFoundException(UUID id) {
        super("Property not found: " + id, HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND");
    }
}
