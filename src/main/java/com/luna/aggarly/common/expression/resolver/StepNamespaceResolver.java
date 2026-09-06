package com.luna.aggarly.common.expression.resolver;

import com.luna.aggarly.common.expression.NestedPropertyExtractor;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StepNamespaceResolver implements NamespaceResolver {

    @Override
    public String namespace() {
        return "step";
    }

    @Override
    public Object resolve(String path, ExpressionContext context) {
        if (context == null || context.stepResults() == null || path == null || path.isBlank()) {
            return null;
        }

        int firstDot = path.indexOf('.');
        String stepId = firstDot >= 0 ? path.substring(0, firstDot) : path;
        String remainingPath = firstDot >= 0 ? path.substring(firstDot + 1) : "";

        Object stepResult = context.stepResults().get(stepId);
        if (stepResult == null) {
            return null;
        }

        if (remainingPath.isBlank()) {
            return stepResult;
        }

        if ("result".equals(remainingPath)) {
            if (stepResult instanceof Map<?, ?> map && map.containsKey("result")) {
                return map.get("result");
            }
            return stepResult;
        }

        if (remainingPath.startsWith("result.")) {
            remainingPath = remainingPath.substring(7);
            if (stepResult instanceof Map<?, ?> map && map.containsKey("result")) {
                stepResult = map.get("result");
            }
        }

        return NestedPropertyExtractor.extract(stepResult, remainingPath);
    }
}
