package com.luna.aggarly.vision.worker;

import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionDeadLetterRetryScheduler {

    private final VisionProcessingTaskRepository taskRepository;

    @Scheduled(cron = "${aggarly.vision.worker.dead-letter-retry-cron:0 0 */6 * * *}")
    @Transactional
    public void retryTransientDeadLetters() {
        log.info("Running scheduled dead-letter retry check");
        List<VisionProcessingTask> deadLetters = taskRepository.findByStatus(VisionTaskStatus.DEAD_LETTER);
        if (deadLetters.isEmpty()) return;

        int retryCount = 0;
        for (VisionProcessingTask task : deadLetters) {
            String error = task.getLastErrorMessage() != null ? task.getLastErrorMessage().toLowerCase() : "";
            // If the error was transient network/timeout/503/connection issue
            if (error.contains("timeout") || error.contains("connection") || error.contains("503") || error.contains("refused")) {
                task.setStatus(VisionTaskStatus.QUEUED);
                task.setAttemptCount(0);
                task.setScheduledAt(Instant.now());
                task.setLeaseUntil(null);
                taskRepository.save(task);
                retryCount++;
            }
        }

        if (retryCount > 0) {
            log.info("Re-queued {} transient dead-letter tasks for retry", retryCount);
        }
    }
}
