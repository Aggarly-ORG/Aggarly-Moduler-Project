package com.luna.aggarly.scheduler.operation;

import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OperationRegistry {

    private final Map<String, WorkflowOperation> operations = new ConcurrentHashMap<>();

    public OperationRegistry(List<WorkflowOperation> operationBeans) {
        if (operationBeans != null) {
            for (WorkflowOperation op : operationBeans) {
                operations.put(op.name(), op);
                log.info("Registered workflow operation: {}", op.name());
            }
        }
    }

    public boolean hasOperation(String name) {
        return operations.containsKey(name);
    }

    public WorkflowOperation getOperation(String name) {
        return operations.get(name);
    }

    public Object execute(String name, Object arguments, ExecutionContext context) {
        WorkflowOperation op = operations.get(name);
        if (op == null) {
            throw new IllegalArgumentException("Unknown workflow operation: " + name);
        }
        return op.execute(arguments, context);
    }

    public Map<String, WorkflowOperation> getAllOperations() {
        return Map.copyOf(operations);
    }
}
