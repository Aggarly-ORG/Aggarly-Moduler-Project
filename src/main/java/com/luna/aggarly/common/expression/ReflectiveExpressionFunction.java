package com.luna.aggarly.common.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import com.luna.aggarly.common.expression.spi.ExpressionFunction;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ReflectiveExpressionFunction implements ExpressionFunction {

    private final String name;
    private final Object targetBean;
    private final Method method;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object execute(List<Object> args, ExpressionContext context) {
        try {
            Parameter[] parameters = method.getParameters();
            Object[] invokeArgs = new Object[parameters.length];
            int argIdx = 0;

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Class<?> paramType = param.getType();

                if (ExpressionContext.class.isAssignableFrom(paramType)) {
                    invokeArgs[i] = context;
                } else if (ExecutionContext.class.isAssignableFrom(paramType)) {
                    invokeArgs[i] = (context instanceof ExecutionContext ec) ? ec : null;
                } else if (param.isVarArgs()) {
                    int remainingArgs = (args != null) ? Math.max(0, args.size() - argIdx) : 0;
                    Class<?> componentType = paramType.getComponentType();
                    Object varargArray = Array.newInstance(componentType, remainingArgs);
                    for (int v = 0; v < remainingArgs; v++) {
                        Object val = (args != null) ? args.get(argIdx + v) : null;
                        Array.set(varargArray, v, convertValue(val, componentType));
                    }
                    invokeArgs[i] = varargArray;
                    argIdx += remainingArgs;
                } else {
                    Object rawVal = (args != null && argIdx < args.size()) ? args.get(argIdx) : null;
                    invokeArgs[i] = convertValue(rawVal, paramType);
                    argIdx++;
                }
            }

            method.setAccessible(true);
            return method.invoke(targetBean, invokeArgs);

        } catch (Exception ex) {
            log.warn("Error invoking expression function '{}': {}", name, ex.getMessage());
            return null;
        }
    }

    private Object convertValue(Object raw, Class<?> targetType) {
        if (raw == null) {
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) return Boolean.FALSE;
                if (targetType == int.class) return 0;
                if (targetType == long.class) return 0L;
                if (targetType == double.class) return 0.0;
                if (targetType == float.class) return 0.0f;
            }
            return null;
        }

        if (targetType.isInstance(raw)) {
            return raw;
        }

        if (targetType == String.class) {
            return String.valueOf(raw);
        }

        if (targetType == Integer.class || targetType == int.class) {
            if (raw instanceof Number num) return num.intValue();
            try { return Integer.parseInt(String.valueOf(raw).trim()); } catch (Exception e) { return 0; }
        }

        if (targetType == Long.class || targetType == long.class) {
            if (raw instanceof Number num) return num.longValue();
            try { return Long.parseLong(String.valueOf(raw).trim()); } catch (Exception e) { return 0L; }
        }

        if (targetType == Double.class || targetType == double.class) {
            if (raw instanceof Number num) return num.doubleValue();
            try { return Double.parseDouble(String.valueOf(raw).trim()); } catch (Exception e) { return 0.0; }
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (raw instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(raw).trim());
        }

        if (targetType == UUID.class) {
            try { return UUID.fromString(String.valueOf(raw).trim()); } catch (Exception e) { return null; }
        }

        if (targetType.isEnum()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> enumClass = (Class<Enum>) targetType;
                return Enum.valueOf(enumClass, String.valueOf(raw).trim().toUpperCase());
            } catch (Exception e) { return null; }
        }

        try {
            return objectMapper.convertValue(raw, targetType);
        } catch (Exception ignored) {
            return raw;
        }
    }
}
