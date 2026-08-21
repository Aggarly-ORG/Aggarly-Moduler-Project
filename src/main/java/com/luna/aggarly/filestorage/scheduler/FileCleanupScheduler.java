package com.luna.aggarly.filestorage.scheduler;

import com.luna.aggarly.filestorage.entity.StoredFile;
import com.luna.aggarly.filestorage.entity.enums.FileStatus;
import com.luna.aggarly.filestorage.repository.StoredFileRepository;
import com.luna.aggarly.filestorage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileCleanupScheduler {

    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 0 * * * *") // Run at the top of every hour
    public void cleanupPendingFiles() {
        log.info("Starting cleanup of orphaned PENDING files");
        
        // Find files older than 24 hours
        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<StoredFile> orphanedFiles = storedFileRepository.findByStatusAndCreatedAtBefore(FileStatus.PENDING, threshold);
        
        int deletedCount = 0;
        for (StoredFile file : orphanedFiles) {
            try {
                fileStorageService.deleteFile(file.getId());
                deletedCount++;
            } catch (Exception e) {
                log.error("Failed to delete orphaned file with ID {}", file.getId(), e);
            }
        }
        
        log.info("Finished cleanup. Deleted {} orphaned files.", deletedCount);
    }
}
