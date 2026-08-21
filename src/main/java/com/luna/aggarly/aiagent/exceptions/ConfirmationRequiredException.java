package com.luna.aggarly.aiagent.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class ConfirmationRequiredException extends AggarlyException {
    private final String toolName;
    private final transient Object params;

    public ConfirmationRequiredException(String toolName, Object params) {
        super("Confirmation required to execute tool: " + toolName, HttpStatus.PRECONDITION_REQUIRED, "CONFIRMATION_REQUIRED");
        this.toolName = toolName;
        this.params = params;
    }

    public String getToolName() {
        return toolName;
    }

    public Object getParams() {
        return params;
    }
}
