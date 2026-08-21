package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AggarlyException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
    }
}
