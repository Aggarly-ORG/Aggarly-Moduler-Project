package com.luna.aggarly.common.expression.resolver;

import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import org.springframework.stereotype.Component;

@Component
public class ContextNamespaceResolver implements NamespaceResolver {

    @Override
    public String namespace() {
        return "context";
    }

    @Override
    public Object resolve(String path, ExpressionContext context) {
        if (context == null) return null;
        if (path == null || path.isBlank()) return context;

        return switch (path.toLowerCase()) {
            case "taskid", "task_id" -> context.taskId() != null ? context.taskId().toString() : null;
            case "executionid", "execution_id" -> context.executionId() != null ? context.executionId().toString() : null;
            case "userid", "user_id" -> context.userId() != null ? context.userId().toString() : null;
            case "timezone" -> context.timezone();
            default -> {
                if (context.variables() != null && context.variables().containsKey(path)) {
                    yield context.variables().get(path);
                }
                yield null;
            }
        };
    }
}
