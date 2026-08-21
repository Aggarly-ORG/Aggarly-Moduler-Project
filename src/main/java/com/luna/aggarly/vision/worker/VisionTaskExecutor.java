package com.luna.aggarly.vision.worker;

import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.event.VisionTaskDeadLetterEvent;
import com.luna.aggarly.vision.exception.VisionExceptionPolicy;
import com.luna.aggarly.vision.exception.VisionRetryPolicy;
import com.luna.aggarly.vision.pipeline.VisionImagePipelineOrchestrator;
import com.luna.aggarly.vision.pipeline.records.VisionAnalysisOptions;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import com.luna.aggarly.vision.vector.EmbeddingMigrationService;
import com.luna.aggarly.vision.vector.PropertyVisualProfileAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionTaskExecutor {

    private final VisionImagePipelineOrchestrator pipelineOrchestrator;
    private final PropertyVisualProfileAggregator profileAggregator;
    private final EmbeddingMigrationService migrationService;
    private final VisionProcessingTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async("visionWorkerExecutor")
    @Transactional
    public CompletableFuture<Void> executeTask(UUID taskId) {
        VisionProcessingTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return CompletableFuture.completedFuture(null);

        log.info("Executing Vision Task: id={}, type={}, imageId={}, attempt={}",
                task.getId(), task.getTaskType(),
                task.getPropertyImage() != null ? task.getPropertyImage().getId() : "N/A",
                task.getAttemptCount());

        try {
            switch (task.getTaskType()) {
                case PROCESS_IMAGE, REPROCESS_IMAGE -> {
                    if (task.getPropertyImage() != null) {
                        pipelineOrchestrator.processImage(
                                task.getPropertyImage().getId(),
                                task.getProperty().getId(),
                                task.getPropertyImage().getObjectKey(),
                                VisionAnalysisOptions.defaultOptions()
                        );
                    }
                }
                case GENERATE_PROPERTY_PROFILE -> {
                    profileAggregator.aggregatePropertyProfile(task.getProperty().getId());
                }
                case MIGRATE_EMBEDDINGS -> {
                    if (task.getPropertyImage() != null) {
                        migrationService.migrateImage(task.getPropertyImage().getId());
                    }
                }
            }

            task.setStatus(VisionTaskStatus.COMPLETED);
            task.setFinishedAt(Instant.now());
            task.setCurrentStage("DONE");
            task.setLeaseUntil(null);
            taskRepository.save(task);

            // Auto-trigger profile re-aggregation after image processing finishes
            if (task.getTaskType() == com.luna.aggarly.vision.entity.enums.VisionTaskType.PROCESS_IMAGE) {
                profileAggregator.aggregatePropertyProfile(task.getProperty().getId());
            }

            log.info("Vision task completed successfully: id={}", task.getId());
        } catch (Exception e) {
            handleTaskException(task, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleTaskException(VisionProcessingTask task, Exception e) {
        log.error("Vision task failed for taskId={}: {}", task.getId(), e.getMessage(), e);

        VisionRetryPolicy policy = VisionRetryPolicy.RETRYABLE;
        if (e instanceof VisionExceptionPolicy vep) {
            policy = vep.getRetryPolicy();
        }

        task.setLastErrorMessage(e.getMessage());
        task.setRetryPolicy(policy.name());
        task.setLeaseUntil(null);

        if (policy == VisionRetryPolicy.RETRYABLE && task.getAttemptCount() < task.getMaxAttempts()) {
            task.setStatus(VisionTaskStatus.QUEUED);
            int backoffSeconds = calculateBackoff(task.getAttemptCount());
            task.setScheduledAt(Instant.now().plusSeconds(backoffSeconds));
            log.info("Task {} scheduled for retry in {} seconds (attempt {})", task.getId(), backoffSeconds, task.getAttemptCount());
        } else {
            task.setStatus(VisionTaskStatus.DEAD_LETTER);
            log.warn("Task {} moved to DEAD_LETTER (attempt={})", task.getId(), task.getAttemptCount());
            eventPublisher.publishEvent(new VisionTaskDeadLetterEvent(
                    task.getId(),
                    task.getProperty().getId(),
                    task.getPropertyImage() != null ? task.getPropertyImage().getId() : null,
                    task.getTaskType().name(),
                    e.getMessage()
            ));
        }

        taskRepository.save(task);
    }

    private int calculateBackoff(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> 30;     // 30s
            case 2 -> 300;    // 5 min
            default -> 600;   // 10 min
        };
    }
}
