package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends AggarlyException {
    public EmailAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
    }
}
