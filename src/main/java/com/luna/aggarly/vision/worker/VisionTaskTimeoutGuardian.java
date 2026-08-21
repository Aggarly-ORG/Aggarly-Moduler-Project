package com.luna.aggarly.vision.worker;

import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionTaskTimeoutGuardian {

    private final VisionProcessingTaskRepository taskRepository;

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void recoverStalledTasks() {
        Instant now = Instant.now();
        Instant rescheduleAt = now.plusSeconds(10);
        int recovered = taskRepository.recoverExpiredLeases(now, rescheduleAt);
        if (recovered > 0) {
            log.warn("VisionTaskTimeoutGuardian recovered {} stalled/expired worker leases and re-queued them", recovered);
        }
    }
}
