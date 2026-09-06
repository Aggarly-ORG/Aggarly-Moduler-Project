package com.luna.aggarly.common.expression.spi;

import java.util.List;

public interface ExpressionFunction {
    String name();
    Object execute(List<Object> args, ExpressionContext context);
}
