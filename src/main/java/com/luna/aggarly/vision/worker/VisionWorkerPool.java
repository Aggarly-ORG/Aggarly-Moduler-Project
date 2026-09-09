package com.luna.aggarly.vision.worker;

import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionWorkerPool {

    private final VisionProcessingTaskRepository taskRepository;
    private final VisionTaskExecutor taskExecutor;

    @Value("${aggarly.vision.worker.max-concurrent:3}")
    private int maxConcurrent = 3;

    @Value("${aggarly.vision.worker.lease-duration-seconds:120}")
    private int leaseDurationSeconds = 120;

    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final String workerId = UUID.randomUUID().toString().substring(0, 8);
    private String workerInstanceId;

    @Transactional
    @Scheduled(fixedDelayString = "${aggarly.vision.worker.poll-interval-ms:5000}")
    public void pollAndDispatch() {
        if (activeTaskCount.get() >= maxConcurrent) {
            return;
        }

        if (workerInstanceId == null) {
            try {
                workerInstanceId = InetAddress.getLocalHost().getHostName() + ":" + workerId;
            } catch (Exception e) {
                workerInstanceId = "worker:" + workerId;
            }
        }

        int availableSlots = maxConcurrent - activeTaskCount.get();
        if (availableSlots <= 0) return;

        List<VisionProcessingTask> queuedTasks = taskRepository.findByStatusOrderByPriorityAscScheduledAtAsc(
                VisionTaskStatus.QUEUED,
                PageRequest.of(0, availableSlots)
        );

        if (queuedTasks.isEmpty()) {
            return;
        }

        for (VisionProcessingTask task : queuedTasks) {
            if (activeTaskCount.get() >= maxConcurrent) break;

            Instant now = Instant.now();
            Instant leaseUntil = now.plusSeconds(leaseDurationSeconds);

            int updatedRows = taskRepository.claimTask(
                    task.getId(),
                    workerId,
                    workerInstanceId,
                    leaseUntil,
                    now,
                    now
            );

            if (updatedRows > 0) {
                activeTaskCount.incrementAndGet();
                log.debug("Worker {} claimed task {}", workerInstanceId, task.getId());
                taskExecutor.executeTask(task.getId())
                        .whenComplete((res, ex) -> activeTaskCount.decrementAndGet());
            }
        }
    }

    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }
}
