package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidMfaTokenException extends AggarlyException {
    public InvalidMfaTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_MFA_TOKEN");
    }
}
