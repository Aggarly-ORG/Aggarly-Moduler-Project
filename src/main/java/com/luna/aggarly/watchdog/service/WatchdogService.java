package com.luna.aggarly.watchdog.service;

import com.luna.aggarly.watchdog.dto.CreateWatchdogRequest;
import com.luna.aggarly.watchdog.dto.WatchdogAlertDto;

import java.util.List;
import java.util.UUID;

public interface WatchdogService {
    List<WatchdogAlertDto> getWatchdogs(UUID userId);
    WatchdogAlertDto createWatchdog(CreateWatchdogRequest req, UUID userId);
    boolean toggleWatchdog(UUID watchdogId, boolean active, UUID userId);
    boolean deleteWatchdog(UUID watchdogId, UUID userId);
    void evaluateWatchdogs();
}