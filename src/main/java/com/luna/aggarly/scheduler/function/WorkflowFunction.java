package com.luna.aggarly.scheduler.function;

import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.ExpressionFunction;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;

import java.util.List;

public interface WorkflowFunction extends ExpressionFunction {

    @Override
    String name();

    Object execute(List<Object> arguments, ExecutionContext context);

    @Override
    default Object execute(List<Object> args, ExpressionContext context) {
        if (context instanceof ExecutionContext ec) {
            return execute(args, ec);
        }
        return execute(args, (ExecutionContext) null);
    }
}
