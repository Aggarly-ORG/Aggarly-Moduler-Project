package com.luna.aggarly.scheduler.function;

import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FunctionRegistry {

    private final Map<String, WorkflowFunction> functions = new ConcurrentHashMap<>();

    public FunctionRegistry(List<WorkflowFunction> functionBeans) {
        if (functionBeans != null) {
            for (WorkflowFunction fn : functionBeans) {
                functions.put(fn.name(), fn);
                log.info("Registered workflow function: {}", fn.name());
            }
        }
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public Object execute(String name, List<Object> arguments, ExecutionContext context) {
        WorkflowFunction fn = functions.get(name);
        if (fn == null) {
            throw new IllegalArgumentException("Unknown workflow function: " + name);
        }
        return fn.execute(arguments != null ? arguments : List.of(), context);
    }

    public Map<String, WorkflowFunction> getAllFunctions() {
        return Map.copyOf(functions);
    }
}
