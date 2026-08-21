package com.luna.aggarly.payment.listener;

import com.luna.aggarly.booking.event.BookingCancelledEvent;
import com.luna.aggarly.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCancellationRefundListener {

    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Received BookingCancelledEvent for bookingId={}, refundAmount={}", event.getBookingId(), event.getRefundAmount());
        try {
            String idempotencyKey = "refund_booking_" + event.getBookingId();
            paymentService.refund(event.getBookingId(), event.getRefundAmount(), event.getReason(), idempotencyKey);
        } catch (Exception e) {
            log.error("Failed to process refund for cancelled bookingId={}", event.getBookingId(), e);
        }
    }
}
