package com.luna.aggarly.scheduler.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;

public interface WorkflowOperation {

    String name();

    String description();

    JsonNode parameterSchema();

    JsonNode responseSchema();

    Object execute(Object arguments, ExecutionContext context);
}
