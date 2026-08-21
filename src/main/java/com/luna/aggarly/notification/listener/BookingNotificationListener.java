package com.luna.aggarly.notification.listener;

import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.event.BookingCancelledEvent;
import com.luna.aggarly.booking.event.BookingConfirmedEvent;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final BookingRepository bookingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Processing BookingConfirmedEvent in notification module: {}", event.getBookingId());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            // Notify Guest
            Map<String, Object> model = new HashMap<>();
            model.put("recipientName", "Guest");
            model.put("bookingId", booking.getId().toString());
            model.put("checkIn", booking.getCheckIn().toString());
            model.put("checkOut", booking.getCheckOut().toString());
            model.put("totalAmount", "$" + booking.getTotalAmount());
            model.put("actionUrl", "/bookings/" + booking.getId());

            dispatcher.dispatch(
                    booking.getGuestId(),
                    NotificationCategory.BOOKING,
                    "BOOKING_CONFIRMED",
                    "Booking Confirmed!",
                    "Your reservation for " + booking.getCheckIn() + " to " + booking.getCheckOut() + " has been confirmed.",
                    "{\"bookingId\":\"" + booking.getId() + "\",\"propertyId\":\"" + booking.getPropertyId() + "\"}",
                    "booking-confirmed",
                    model
            );

            // Notify Host
            dispatcher.dispatch(
                    booking.getHostId(),
                    NotificationCategory.BOOKING,
                    "NEW_BOOKING_RESERVATION",
                    "New Reservation Received!",
                    "A guest has booked your property from " + booking.getCheckIn() + " to " + booking.getCheckOut() + ".",
                    "{\"bookingId\":\"" + booking.getId() + "\",\"propertyId\":\"" + booking.getPropertyId() + "\"}",
                    null,
                    null
            );
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Processing BookingCancelledEvent in notification module: {}", event.getBookingId());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            dispatcher.dispatch(
                    booking.getGuestId(),
                    NotificationCategory.BOOKING,
                    "BOOKING_CANCELLED",
                    "Booking Cancelled",
                    "Your booking has been cancelled. Refund amount: $" + event.getRefundAmount(),
                    "{\"bookingId\":\"" + booking.getId() + "\"}",
                    null,
                    null
            );

            dispatcher.dispatch(
                    booking.getHostId(),
                    NotificationCategory.BOOKING,
                    "BOOKING_CANCELLED_BY_GUEST",
                    "Reservation Cancelled",
                    "A booking on your property has been cancelled.",
                    "{\"bookingId\":\"" + booking.getId() + "\"}",
                    null,
                    null
            );
        });
    }
}
