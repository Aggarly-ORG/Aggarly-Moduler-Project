package com.luna.aggarly.common.expression.resolver;

import com.luna.aggarly.common.expression.NestedPropertyExtractor;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.NamespaceResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ObjNamespaceResolver implements NamespaceResolver {

    @Override
    public String namespace() {
        return "obj";
    }

    @Override
    public Object resolve(String path, ExpressionContext context) {
        if (context == null || context.rootObject() == null) {
            return null;
        }
        Map<String, Object> root = context.rootObject();
        if (path == null || path.isBlank()) {
            return root;
        }
        return NestedPropertyExtractor.extract(root, path);
    }
}
