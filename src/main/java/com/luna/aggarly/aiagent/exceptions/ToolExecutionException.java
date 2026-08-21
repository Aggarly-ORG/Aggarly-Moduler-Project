package com.luna.aggarly.aiagent.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class ToolExecutionException extends AggarlyException {
    public ToolExecutionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_EXECUTION_ERROR");
    }
}
