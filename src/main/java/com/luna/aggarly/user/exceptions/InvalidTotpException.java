package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidTotpException extends AggarlyException {
    public InvalidTotpException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_TOTP");
    }
}
