package com.luna.aggarly.cleaning.listener;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.event.BookingCancelledEvent;
import com.luna.aggarly.booking.event.BookingConfirmedEvent;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.cleaning.service.CleaningTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCleaningEventListener {

    private final CleaningTaskService cleaningTaskService;
    private final BookingRepository bookingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received BookingConfirmedEvent for booking {}", event.getBookingId());
        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            cleaningTaskService.scheduleTurnoverForBooking(
                    booking.getId(),
                    booking.getPropertyId(),
                    booking.getHostId(),
                    booking.getCheckOut()
            );
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Received BookingCancelledEvent for booking {}", event.getBookingId());
        cleaningTaskService.cancelTurnoverForBooking(event.getBookingId());
    }
}
