package com.luna.aggarly.booking.listener;

import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.payment.event.PaymentFailedEvent;
import com.luna.aggarly.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingService bookingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for bookingId={}", event.getBookingId());
        bookingService.confirmBooking(event.getBookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for bookingId={}", event.getBookingId());
        String reason = "Payment failed: " + event.getErrorCode();
        bookingService.cancelDueToPaymentFailure(event.getBookingId(), reason);
    }
}
