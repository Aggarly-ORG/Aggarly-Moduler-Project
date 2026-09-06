package com.luna.aggarly.user.service;

import com.luna.aggarly.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled maintenance service that periodically purges expired refresh tokens
 * and tokens that have been revoked for longer than the retention window,
 * preventing unbounded table growth and optimizing index performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.cleanup-retention-days:7}")
    private int retentionDays;

    /**
     * Daily cleanup job running at 03:00 AM server time.
     */
    @Scheduled(cron = "${app.jwt.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupRevokedAndExpiredTokens() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));

        log.debug("Starting scheduled cleanup of refresh tokens older than cutoff: {}", cutoff);
        try {
            int deletedCount = refreshTokenRepository.deleteExpiredAndOldRevokedTokens(now, cutoff);
            if (deletedCount > 0) {
                log.info("🧹 Cleaned up {} expired and old revoked refresh tokens from database", deletedCount);
            } else {
                log.debug("No expired or revoked refresh tokens required cleanup");
            }
        } catch (Exception e) {
            log.error("Failed to clean up expired and revoked refresh tokens: {}", e.getMessage(), e);
        }
    }
}
