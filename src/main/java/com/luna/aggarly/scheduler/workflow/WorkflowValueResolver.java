package com.luna.aggarly.scheduler.workflow;

import com.luna.aggarly.common.expression.EvaluationMode;
import com.luna.aggarly.common.expression.ExpressionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowValueResolver {

    private final ExpressionEngine expressionEngine;

    /**
     * Recursively resolves dynamic expressions in strings, lists, and maps using the hardened ExpressionEngine.
     */
    public Object resolve(Object value, ExecutionContext context) {
        return expressionEngine.resolve(value, context, EvaluationMode.LENIENT);
    }

    public Object resolve(Object value, ExecutionContext context, EvaluationMode mode) {
        return expressionEngine.resolve(value, context, mode);
    }
}
