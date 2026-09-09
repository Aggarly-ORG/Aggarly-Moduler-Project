package com.luna.aggarly.watchdog.task;

import com.luna.aggarly.watchdog.service.WatchdogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchdogEvaluationTask {

    private final WatchdogService watchdogService;

    // Runs every 15 minutes (900,000 ms) to evaluate price & availability targets
    @Scheduled(fixedDelay = 900000, initialDelay = 60000)
    public void evaluateActiveWatchdogs() {
        try {
            log.info("Starting scheduled evaluation of active price/availability watchdogs...");
            watchdogService.evaluateWatchdogs();
        } catch (Exception e) {
            log.error("Error evaluating watchdogs", e);
        }
    }
}