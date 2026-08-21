package com.luna.aggarly.scheduler.service;

import com.luna.aggarly.scheduler.dto.*;
import com.luna.aggarly.user.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ScheduledTaskService {

    ScheduledTaskResponse createTask(CreateScheduledTaskRequest request, UserPrincipal user);

    ScheduledTaskResponse updateTask(UUID taskId, UpdateScheduledTaskRequest request, UserPrincipal user);

    ScheduledTaskResponse getTaskById(UUID taskId, UUID userId);

    Page<ScheduledTaskSummaryResponse> listMyTasks(UUID userId, Pageable pageable);

    ScheduledTaskResponse pauseTask(UUID taskId, UUID userId);

    ScheduledTaskResponse resumeTask(UUID taskId, UUID userId);

    void cancelTask(UUID taskId, UUID userId);

    void runNow(UUID taskId, UUID userId);

    Page<TaskExecutionResponse> getTaskExecutions(UUID taskId, UUID userId, Pageable pageable);
}
