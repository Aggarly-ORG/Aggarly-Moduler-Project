package com.luna.aggarly.booking.service.impl;

import com.luna.aggarly.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic scheduled job that cleans up expired, unconfirmed bookings whose payment
 * was not completed within the allowed time window, automatically releasing blocked
 * property calendar dates and canceling orphaned payment intents.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationJob {

    private final BookingService bookingService;

    @Value("${app.booking.payment-timeout-minutes:15}")
    private int timeoutMinutes;

    @Scheduled(fixedDelayString = "${app.booking.expiration-poll-interval-ms:60000}", initialDelay = 10000)
    public void cleanupUnconfirmedBookings() {
        try {
            bookingService.expireUnconfirmedBookings(timeoutMinutes);
        } catch (Exception e) {
            log.error("Error occurred during scheduled unconfirmed booking cleanup", e);
        }
    }
}
