package com.luna.aggarly.common.expression.resolver;

import com.luna.aggarly.common.expression.NestedPropertyExtractor;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import org.springframework.stereotype.Component;

@Component
public class EventNamespaceResolver implements NamespaceResolver {

    @Override
    public String namespace() {
        return "event";
    }

    @Override
    public Object resolve(String path, ExpressionContext context) {
        if (context == null) return null;
        Object event = context.event();
        if (event == null && context.rootObject() != null && context.rootObject().containsKey("event")) {
            event = context.rootObject().get("event");
        }
        if (event == null) return null;
        if (path == null || path.isBlank()) return event;

        return NestedPropertyExtractor.extract(event, path);
    }
}
