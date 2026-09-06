package com.luna.aggarly.scheduler.engine;

import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SchedulerEngine {

    private final DueTaskClaimer dueTaskClaimer;
    private final WorkflowTaskExecutor workflowTaskExecutor;
    private final ExecutorService workerPool;
    private final int batchSize;

    public SchedulerEngine(
            DueTaskClaimer dueTaskClaimer,
            WorkflowTaskExecutor workflowTaskExecutor,
            @Value("${app.scheduler.worker-threads:10}") int workerThreads,
            @Value("${app.scheduler.batch-size:20}") int batchSize
    ) {
        this.dueTaskClaimer = dueTaskClaimer;
        this.workflowTaskExecutor = workflowTaskExecutor;
        this.batchSize = batchSize;
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
        log.info("Initialized SchedulerEngine with workerPool size={}", workerThreads);
    }

    //@org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms:5000}", initialDelay = 5000)
    public void pollAndDispatch() {
        try {
            List<ScheduledTask> claimedTasks = dueTaskClaimer.claimDueTasks(batchSize);
            for (ScheduledTask task : claimedTasks) {
                workerPool.submit(() -> {
                    try {
                        ExecutionContext context = ExecutionContext.forTask(
                                task.getUserId(),
                                task.getId(),
                                UUID.randomUUID(),
                                task.getTimezone()
                        );
                        workflowTaskExecutor.execute(task, context);
                    } catch (Exception ex) {
                        log.error("Unhandled worker error on task {}", task.getId(), ex);
                    }
                });
            }
        } catch (Exception ex) {
            log.error("Scheduler tick failed", ex);
        }
    }

    public void wakeUpNow() {
        workerPool.submit(this::pollAndDispatch);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SchedulerEngine worker pool...");
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
