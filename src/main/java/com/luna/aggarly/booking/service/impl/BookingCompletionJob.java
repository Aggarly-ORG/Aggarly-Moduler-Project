package com.luna.aggarly.booking.service.impl;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import com.luna.aggarly.booking.event.BookingCompletedEvent;
import com.luna.aggarly.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCompletionJob {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void completePastBookings() {
        log.info("Running night job to complete past bookings...");
        List<Booking> confirmedPastCheckout = bookingRepository.findByStatusAndCheckOutBefore(
                BookingStatus.CONFIRMED, LocalDate.now());

        for (Booking booking : confirmedPastCheckout) {
            log.info("Transitioning bookingId={} to COMPLETED", booking.getId());
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            eventPublisher.publishEvent(new BookingCompletedEvent(this, booking.getId()));
        }
    }
}
