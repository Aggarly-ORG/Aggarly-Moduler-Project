package com.luna.aggarly.aiagent.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UnauthorizedToolAccessException extends AggarlyException {
    public UnauthorizedToolAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_TOOL_ACCESS");
    }
}
