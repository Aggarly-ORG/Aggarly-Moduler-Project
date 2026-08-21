package com.luna.aggarly.scheduler.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.dto.*;
import com.luna.aggarly.scheduler.engine.SchedulerEngine;
import com.luna.aggarly.scheduler.engine.WorkflowTaskExecutor;
import com.luna.aggarly.scheduler.engine.TriggerCalculator;
import com.luna.aggarly.scheduler.entity.ScheduledEventTrigger;
import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.entity.ScheduledTaskExecution;
import com.luna.aggarly.scheduler.entity.enums.ExecutionType;
import com.luna.aggarly.scheduler.entity.enums.MisfirePolicy;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import com.luna.aggarly.scheduler.repository.ScheduledEventTriggerRepository;
import com.luna.aggarly.scheduler.repository.ScheduledTaskExecutionRepository;
import com.luna.aggarly.scheduler.repository.ScheduledTaskRepository;
import com.luna.aggarly.scheduler.service.ScheduledTaskService;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import com.luna.aggarly.scheduler.workflow.PlanValidator;
import com.luna.aggarly.scheduler.workflow.WorkflowPlan;
import com.luna.aggarly.user.security.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskExecutionRepository executionRepository;
    private final ScheduledEventTriggerRepository eventTriggerRepository;
    private final PlanValidator planValidator;
    private final TriggerCalculator triggerCalculator;
    private final WorkflowTaskExecutor workflowTaskExecutor;
    private final SchedulerEngine schedulerEngine;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ScheduledTaskResponse createTask(CreateScheduledTaskRequest request, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        if (userId == null) {
            throw new SecurityException("User authentication required to create scheduled tasks.");
        }

        try {
            String planJson = objectMapper.writeValueAsString(request.plan());
            WorkflowPlan plan = objectMapper.readValue(planJson, WorkflowPlan.class);

            // 1. Validate Plan & Security boundaries
            planValidator.validatePlan(plan, user);

            String triggerConfigJson = objectMapper.writeValueAsString(
                    request.triggerConfig() != null ? request.triggerConfig() : Map.of()
            );
            String timezone = request.timezone() != null ? request.timezone() : "UTC";

            // 2. Validate Trigger
            planValidator.validateTrigger(request.triggerType(), triggerConfigJson, timezone);

            // 3. Compute initial nextExecutionAt
            Instant initialNextRun = triggerCalculator.calculateNextExecutionAt(
                    request.triggerType(),
                    triggerConfigJson,
                    null,
                    timezone
            );

            ScheduledTask task = ScheduledTask.builder()
                    .userId(userId)
                    .name(request.name())
                    .description(request.description())
                    .status(TaskStatus.ACTIVE)
                    .triggerType(request.triggerType())
                    .executionType(request.executionType() != null ? request.executionType() : ExecutionType.DETERMINISTIC)
                    .misfirePolicy(request.misfirePolicy() != null ? request.misfirePolicy() : MisfirePolicy.RUN_ONCE_NOW)
                    .timezone(timezone)
                    .nextExecutionAt(initialNextRun)
                    .maxRetries(request.maxRetries() != null ? request.maxRetries() : 3)
                    .triggerConfig(triggerConfigJson)
                    .plan(planJson)
                    .planVersion(1)
                    .build();

            ScheduledTask savedTask = taskRepository.save(task);

            // 4. If EVENT / EVENT_OFFSET, register in scheduled_event_trigger table
            if (request.triggerType() == TriggerType.EVENT || request.triggerType() == TriggerType.EVENT_OFFSET) {
                if (request.triggerConfig() != null && request.triggerConfig().containsKey("event")) {
                    String eventType = String.valueOf(request.triggerConfig().get("event"));
                    ScheduledEventTrigger trigger = ScheduledEventTrigger.builder()
                            .task(savedTask)
                            .eventType(eventType)
                            .filterConfig(triggerConfigJson)
                            .build();
                    eventTriggerRepository.save(trigger);
                }
            }

            // Wake scheduler to recalculate due batch
            schedulerEngine.wakeUpNow();

            log.info("Created scheduled task '{}' (id: {}) for user {}", savedTask.getName(), savedTask.getId(), userId);
            return mapToResponse(savedTask);

        } catch (Exception ex) {
            log.error("Failed to create scheduled task", ex);
            if (ex instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to create scheduled task: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public ScheduledTaskResponse updateTask(UUID taskId, UpdateScheduledTaskRequest request, UserPrincipal user) {
        UUID userId = user != null ? user.getUserId() : null;
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));

        try {
            if (request.name() != null) task.setName(request.name());
            if (request.description() != null) task.setDescription(request.description());
            if (request.timezone() != null) task.setTimezone(request.timezone());
            if (request.maxRetries() != null) task.setMaxRetries(request.maxRetries());

            if (request.plan() != null) {
                String planJson = objectMapper.writeValueAsString(request.plan());
                WorkflowPlan plan = objectMapper.readValue(planJson, WorkflowPlan.class);
                planValidator.validatePlan(plan, user);
                task.setPlan(planJson);
                task.setPlanVersion(task.getPlanVersion() + 1);
            }

            if (request.triggerType() != null || request.triggerConfig() != null) {
                TriggerType triggerType = request.triggerType() != null ? request.triggerType() : task.getTriggerType();
                String triggerConfigJson = request.triggerConfig() != null
                        ? objectMapper.writeValueAsString(request.triggerConfig())
                        : task.getTriggerConfig();

                planValidator.validateTrigger(triggerType, triggerConfigJson, task.getTimezone());
                task.setTriggerType(triggerType);
                task.setTriggerConfig(triggerConfigJson);

                Instant nextRun = triggerCalculator.calculateNextExecutionAt(
                        triggerType, triggerConfigJson, task.getLastExecutionAt(), task.getTimezone()
                );
                task.setNextExecutionAt(nextRun);
            }

            ScheduledTask updated = taskRepository.save(task);
            schedulerEngine.wakeUpNow();
            return mapToResponse(updated);

        } catch (Exception ex) {
            log.error("Failed to update scheduled task {}", taskId, ex);
            if (ex instanceof RuntimeException re) throw re;
            throw new RuntimeException("Failed to update scheduled task: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduledTaskResponse getTaskById(UUID taskId, UUID userId) {
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));
        return mapToResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScheduledTaskSummaryResponse> listMyTasks(UUID userId, Pageable pageable) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToSummary);
    }

    @Override
    @Transactional
    public ScheduledTaskResponse pauseTask(UUID taskId, UUID userId) {
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Cannot pause task: Task is already " + task.getStatus());
        }

        task.setStatus(TaskStatus.PAUSED);
        return mapToResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public ScheduledTaskResponse resumeTask(UUID taskId, UUID userId) {
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Cannot resume task: Task is already COMPLETED.");
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Cannot resume task: Task is CANCELLED.");
        }

        task.setStatus(TaskStatus.ACTIVE);
        Instant nextRun = triggerCalculator.calculateNextExecutionAt(
                task.getTriggerType(), task.getTriggerConfig(), task.getLastExecutionAt(), task.getTimezone()
        );
        task.setNextExecutionAt(nextRun);
        ScheduledTask saved = taskRepository.save(task);
        schedulerEngine.wakeUpNow();
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void cancelTask(UUID taskId, UUID userId) {
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));
        task.setStatus(TaskStatus.CANCELLED);
        task.setNextExecutionAt(null);
        taskRepository.save(task);
    }

    @Override
    public void runNow(UUID taskId, UUID userId) {
        ScheduledTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Cannot run task: Task is already COMPLETED.");
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Cannot run task: Task is CANCELLED.");
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new IllegalStateException("Task is currently RUNNING.");
        }

        ExecutionContext context = ExecutionContext.forTask(
                userId, task.getId(), UUID.randomUUID(), task.getTimezone()
        );
        workflowTaskExecutor.execute(task, context);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskExecutionResponse> getTaskExecutions(UUID taskId, UUID userId, Pageable pageable) {
        // Verify ownership
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled task not found: " + taskId));

        return executionRepository.findByTaskIdOrderByStartedAtDesc(taskId, pageable)
                .map(this::mapToExecutionResponse);
    }

    private ScheduledTaskResponse mapToResponse(ScheduledTask task) {
        Map<String, Object> triggerMap = Map.of();
        Map<String, Object> planMap = Map.of();
        try {
            triggerMap = objectMapper.readValue(task.getTriggerConfig(), Map.class);
            planMap = objectMapper.readValue(task.getPlan(), Map.class);
        } catch (Exception ignored) {}

        return ScheduledTaskResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .name(task.getName())
                .description(task.getDescription())
                .status(task.getStatus())
                .triggerType(task.getTriggerType())
                .executionType(task.getExecutionType())
                .misfirePolicy(task.getMisfirePolicy())
                .timezone(task.getTimezone())
                .nextExecutionAt(task.getNextExecutionAt())
                .lastExecutionAt(task.getLastExecutionAt())
                .lastStartedAt(task.getLastStartedAt())
                .lastFinishedAt(task.getLastFinishedAt())
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .lastError(task.getLastError())
                .triggerConfig(triggerMap)
                .plan(planMap)
                .planVersion(task.getPlanVersion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private ScheduledTaskSummaryResponse mapToSummary(ScheduledTask task) {
        return ScheduledTaskSummaryResponse.builder()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .status(task.getStatus())
                .triggerType(task.getTriggerType())
                .timezone(task.getTimezone())
                .nextExecutionAt(task.getNextExecutionAt())
                .lastExecutionAt(task.getLastExecutionAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private TaskExecutionResponse mapToExecutionResponse(ScheduledTaskExecution exec) {
        Map<String, Object> stepResults = Map.of();
        try {
            if (exec.getStepResults() != null) {
                stepResults = objectMapper.readValue(exec.getStepResults(), Map.class);
            }
        } catch (Exception ignored) {}

        return TaskExecutionResponse.builder()
                .id(exec.getId())
                .taskId(exec.getTask().getId())
                .planVersion(exec.getPlanVersion())
                .status(exec.getStatus())
                .attempt(exec.getAttempt())
                .startedAt(exec.getStartedAt())
                .finishedAt(exec.getFinishedAt())
                .durationMs(exec.getDurationMs())
                .errorCode(exec.getErrorCode())
                .errorMessage(exec.getErrorMessage())
                .stepResults(stepResults)
                .correlationId(exec.getCorrelationId())
                .build();
    }
}
