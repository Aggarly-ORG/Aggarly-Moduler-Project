package com.luna.aggarly.common.expression.spi;

import java.util.Map;
import java.util.UUID;

public interface ExpressionContext {
    UUID userId();
    UUID taskId();
    UUID executionId();
    String timezone();
    Map<String, Object> rootObject();
    Map<String, Object> stepResults();
    Object event();
    Map<String, Object> variables();
    Object getVariable(String name);
    void setVariable(String name, Object value);
}
