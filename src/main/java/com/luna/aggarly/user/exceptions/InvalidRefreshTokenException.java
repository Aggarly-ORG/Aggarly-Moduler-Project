package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AggarlyException {
    public InvalidRefreshTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
    }
}
