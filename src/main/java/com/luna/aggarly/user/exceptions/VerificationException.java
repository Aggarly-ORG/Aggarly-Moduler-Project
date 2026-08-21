package com.luna.aggarly.user.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class VerificationException extends AggarlyException {
    public VerificationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VERIFICATION_FAILED");
    }
}
