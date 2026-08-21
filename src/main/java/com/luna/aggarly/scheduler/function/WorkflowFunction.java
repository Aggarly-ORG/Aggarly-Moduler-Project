package com.luna.aggarly.scheduler.function;

import com.luna.aggarly.scheduler.workflow.ExecutionContext;

import java.util.List;

public interface WorkflowFunction {

    String name();

    Object execute(List<Object> arguments, ExecutionContext context);
}
