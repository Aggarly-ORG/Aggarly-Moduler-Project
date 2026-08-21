package com.luna.aggarly.scheduler.function.impl;

import com.luna.aggarly.scheduler.function.WorkflowFunction;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;

public class SecurityFunctions {

    @Component
    public static class GetCurrentUserIdFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "SecurityUtils.getCurrentUserId";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            if (context != null && context.userId() != null) {
                return context.userId().toString();
            }
            return null;
        }
    }
}
