package com.luna.aggarly.aiagent.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ToolInvocationNotFoundException extends AggarlyException {
    public ToolInvocationNotFoundException(UUID id) {
        super("Tool invocation not found with id: " + id, HttpStatus.NOT_FOUND, "TOOL_INVOCATION_NOT_FOUND");
    }
}
