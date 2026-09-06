package com.luna.aggarly.common.expression.resolver;

import com.luna.aggarly.common.expression.NestedPropertyExtractor;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import org.springframework.stereotype.Component;

@Component
public class UserNamespaceResolver implements NamespaceResolver {

    @Override
    public String namespace() {
        return "user";
    }

    @Override
    public Object resolve(String path, ExpressionContext context) {
        if (context == null) return null;
        if (path == null || path.isBlank() || "id".equalsIgnoreCase(path) || "userId".equalsIgnoreCase(path)) {
            return context.userId() != null ? context.userId().toString() : null;
        }

        if (context.rootObject() != null && context.rootObject().containsKey("user")) {
            Object userObj = context.rootObject().get("user");
            return NestedPropertyExtractor.extract(userObj, path);
        }
        return null;
    }
}
