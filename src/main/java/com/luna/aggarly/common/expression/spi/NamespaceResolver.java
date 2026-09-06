package com.luna.aggarly.common.expression.spi;

public interface NamespaceResolver {
    String namespace();
    Object resolve(String path, ExpressionContext context);
}
