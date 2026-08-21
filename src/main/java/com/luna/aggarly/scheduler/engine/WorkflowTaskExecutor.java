package com.luna.aggarly.scheduler.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.entity.ScheduledTaskExecution;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.operation.OperationRegistry;
import com.luna.aggarly.scheduler.repository.ScheduledTaskExecutionRepository;
import com.luna.aggarly.scheduler.repository.ScheduledTaskRepository;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import com.luna.aggarly.scheduler.workflow.WorkflowPlan;
import com.luna.aggarly.scheduler.workflow.WorkflowStep;
import com.luna.aggarly.scheduler.workflow.WorkflowValueResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTaskExecutor {

    private final OperationRegistry operationRegistry;
    private final WorkflowValueResolver valueResolver;
    private final TriggerCalculator triggerCalculator;
    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(ScheduledTask taskInput, ExecutionContext context) {
        if (taskInput == null || taskInput.getId() == null) {
            log.warn("Cannot execute task: task input is null.");
            return;
        }

        // Fetch authoritative latest entity from database with row lock
        ScheduledTask task = taskRepository.findById(taskInput.getId()).orElse(null);
        if (task == null) {
            log.warn("Cannot execute task {}: Entity not found in database.", taskInput.getId());
            return;
        }

        // 1. HARD RESTRICTION: Never execute tasks that are already COMPLETED or CANCELLED
        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Refusing execution for task '{}' (id: {}): Task is already marked COMPLETED.", task.getName(), task.getId());
            return;
        }

        if (task.getStatus() == TaskStatus.CANCELLED) {
            log.warn("Refusing execution for task '{}' (id: {}): Task is CANCELLED.", task.getName(), task.getId());
            return;
        }

        // 2. HARD RESTRICTION: One-time (ONCE) tasks that were already executed must not re-run
        if (task.getTriggerType() == TriggerType.ONCE && task.getLastExecutionAt() != null) {
            log.warn("Refusing re-execution for one-time task '{}' (id: {}): Already executed at {}. Marking COMPLETED.",
                    task.getName(), task.getId(), task.getLastExecutionAt());
            task.setStatus(TaskStatus.COMPLETED);
            task.setNextExecutionAt(null);
            taskRepository.save(task);
            return;
        }

        Instant startTime = Instant.now();
        log.info("Executing scheduled task '{}' (id: {}, executionId: {})", task.getName(), task.getId(), context.executionId());

        task.setStatus(TaskStatus.RUNNING);
        task.setLastStartedAt(startTime);
        task = taskRepository.save(task);

        ScheduledTaskExecution execution = ScheduledTaskExecution.builder()
                .task(task)
                .planVersion(task.getPlanVersion())
                .status(TaskStatus.RUNNING)
                .attempt(task.getRetryCount() + 1)
                .startedAt(startTime)
                .correlationId(context.executionId().toString())
                .build();
        execution = executionRepository.save(execution);

        try {
            WorkflowPlan plan = objectMapper.readValue(
                    task.getPlan() != null ? task.getPlan() : "{}",
                    WorkflowPlan.class
            );

            // Populate comprehensive rootObject ('obj') in ExecutionContext
            populateRootObject(context, task, plan, startTime);

            // Execute sequential steps
            for (WorkflowStep step : plan.steps()) {
                log.debug("Executing step '{}' (service: {}) for task {}", step.id(), step.service(), task.getId());

                Object resolvedArgs = valueResolver.resolve(step.arguments(), context);
                Object result = operationRegistry.execute(step.service(), resolvedArgs, context);

                if (result != null) {
                    context.stepResults().put(step.id(), result);
                }
            }

            Instant finishTime = Instant.now();
            long durationMs = Duration.between(startTime, finishTime).toMillis();

            // Record successful execution
            execution.setStatus(TaskStatus.COMPLETED);
            execution.setFinishedAt(finishTime);
            execution.setDurationMs(durationMs);
            execution.setStepResults(objectMapper.writeValueAsString(context.stepResults()));
            executionRepository.save(execution);

            // Update task schedule status
            task.setLastExecutionAt(startTime);
            task.setLastFinishedAt(finishTime);
            task.setRetryCount(0);
            task.setLastError(null);

            if (task.getTriggerType() == TriggerType.ONCE) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setNextExecutionAt(null);
            } else if (task.getTriggerType() == TriggerType.EVENT || task.getTriggerType() == TriggerType.EVENT_OFFSET) {
                task.setStatus(TaskStatus.ACTIVE);
            } else {
                Instant nextRun = triggerCalculator.calculateNextExecutionAt(
                        task.getTriggerType(),
                        task.getTriggerConfig(),
                        startTime,
                        task.getTimezone()
                );
                task.setNextExecutionAt(nextRun);
                task.setStatus(TaskStatus.ACTIVE);
            }

            taskRepository.save(task);
            log.info("Successfully executed task '{}' in {}ms. Status is now: {}", task.getName(), durationMs, task.getStatus());

        } catch (Exception ex) {
            Instant finishTime = Instant.now();
            long durationMs = Duration.between(startTime, finishTime).toMillis();
            log.error("Task execution failed for '{}' (id: {}): {}", task.getName(), task.getId(), ex.getMessage(), ex);

            execution.setStatus(TaskStatus.FAILED);
            execution.setFinishedAt(finishTime);
            execution.setDurationMs(durationMs);
            execution.setErrorCode(ex.getClass().getSimpleName());
            execution.setErrorMessage(ex.getMessage());
            executionRepository.save(execution);

            int currentRetries = task.getRetryCount() + 1;
            task.setRetryCount(currentRetries);
            task.setLastError(ex.getMessage());
            task.setLastFinishedAt(finishTime);

            if (currentRetries >= task.getMaxRetries() || task.getTriggerType() == TriggerType.ONCE) {
                task.setStatus(TaskStatus.FAILED);
                task.setNextExecutionAt(null);
                log.warn("Task '{}' failed (retries: {}/{}) and is now marked FAILED.", task.getName(), currentRetries, task.getMaxRetries());
            } else {
                // Backoff retry: 30s * attempt
                long backoffSeconds = 30L * currentRetries;
                task.setNextExecutionAt(Instant.now().plusSeconds(backoffSeconds));
                task.setStatus(TaskStatus.ACTIVE);
                log.info("Task '{}' scheduled for retry #{} in {} seconds.", task.getName(), currentRetries, backoffSeconds);
            }

            taskRepository.save(task);
        }
    }

    private void populateRootObject(ExecutionContext context, ScheduledTask task, WorkflowPlan plan, Instant startTime) {
        Map<String, Object> rootObj = context.rootObject();
        rootObj.clear();

        rootObj.put("userId", task.getUserId() != null ? task.getUserId().toString() : null);
        rootObj.put("user", Map.of("id", task.getUserId() != null ? task.getUserId().toString() : ""));
        rootObj.put("taskId", task.getId() != null ? task.getId().toString() : null);
        rootObj.put("executionId", context.executionId() != null ? context.executionId().toString() : null);
        rootObj.put("executionTime", startTime.toString());
        rootObj.put("timezone", task.getTimezone());

        // Trigger information
        Map<String, Object> triggerMap = new java.util.HashMap<>();
        triggerMap.put("type", task.getTriggerType() != null ? task.getTriggerType().name() : null);
        try {
            if (task.getTriggerConfig() != null && !task.getTriggerConfig().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cfg = objectMapper.readValue(task.getTriggerConfig(), Map.class);
                triggerMap.put("config", cfg);

                // Flatten direct trigger attributes: obj.trigger.event, obj.trigger.offset, etc.
                cfg.forEach(triggerMap::putIfAbsent);
            }
        } catch (Exception ignored) {}
        rootObj.put("trigger", triggerMap);

        // Plan information
        try {
            if (task.getPlan() != null && !task.getPlan().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> planMap = objectMapper.readValue(task.getPlan(), Map.class);
                rootObj.put("plan", planMap);
            }
        } catch (Exception ignored) {}

        // Event payload if triggered by event
        if (context.event() != null) {
            rootObj.put("event", context.event());
        }

        // Live step results reference
        rootObj.put("steps", context.stepResults());
        rootObj.put("step", context.stepResults());

        // Task metadata
        Map<String, Object> taskMeta = new java.util.HashMap<>();
        taskMeta.put("id", task.getId() != null ? task.getId().toString() : null);
        taskMeta.put("name", task.getName());
        taskMeta.put("description", task.getDescription());
        taskMeta.put("status", task.getStatus() != null ? task.getStatus().name() : null);
        taskMeta.put("triggerType", task.getTriggerType() != null ? task.getTriggerType().name() : null);
        taskMeta.put("timezone", task.getTimezone());
        rootObj.put("task", taskMeta);
    }
}
