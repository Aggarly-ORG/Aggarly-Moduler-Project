package com.luna.aggarly.scheduler.engine;

import com.luna.aggarly.scheduler.entity.ScheduledTask;
import com.luna.aggarly.scheduler.entity.enums.TaskStatus;
import com.luna.aggarly.scheduler.repository.ScheduledTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DueTaskClaimer {

    private final ScheduledTaskRepository scheduledTaskRepository;

    @Transactional
    public List<ScheduledTask> claimDueTasks(int batchSize) {
        Instant now = Instant.now();
        List<ScheduledTask> dueTasks = scheduledTaskRepository.claimDueTasksForUpdate(now, batchSize);

        if (!dueTasks.isEmpty()) {
            log.info("Claimed {} due task(s) for execution via SKIP LOCKED", dueTasks.size());
            for (ScheduledTask task : dueTasks) {
                task.setStatus(TaskStatus.RUNNING);
                task.setLastStartedAt(now);
            }
            return scheduledTaskRepository.saveAll(dueTasks);
        }

        return List.of();
    }
}
