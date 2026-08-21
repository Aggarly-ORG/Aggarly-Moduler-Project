package com.luna.aggarly.notification.listener;

import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.notification.entity.enums.NotificationCategory;
import com.luna.aggarly.notification.service.NotificationDispatcher;
import com.luna.aggarly.payment.event.PaymentFailedEvent;
import com.luna.aggarly.payment.event.PaymentSucceededEvent;
import com.luna.aggarly.payment.event.RefundSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationDispatcher dispatcher;
    private final BookingRepository bookingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Processing PaymentSucceededEvent in notification module: {}", event.getPaymentId());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            Map<String, Object> model = new HashMap<>();
            model.put("recipientName", "Guest");
            model.put("amount", "$" + event.getAmount());
            model.put("paymentId", event.getPaymentId().toString());
            model.put("paymentMethod", "Credit Card");
            model.put("paymentDate", LocalDate.now().toString());

            dispatcher.dispatch(
                    booking.getGuestId(),
                    NotificationCategory.PAYMENT,
                    "PAYMENT_SUCCEEDED",
                    "Payment Received — $" + event.getAmount(),
                    "Your payment of $" + event.getAmount() + " for reservation #" + event.getBookingId() + " was processed successfully.",
                    "{\"paymentId\":\"" + event.getPaymentId() + "\",\"bookingId\":\"" + event.getBookingId() + "\"}",
                    "payment-receipt",
                    model
            );
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Processing PaymentFailedEvent in notification module: {}", event.getBookingId());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            String error = event.getErrorMessage() != null ? event.getErrorMessage() : "Transaction declined";
            dispatcher.dispatch(
                    booking.getGuestId(),
                    NotificationCategory.PAYMENT,
                    "PAYMENT_FAILED",
                    "Payment Failed",
                    "Your payment could not be processed: " + error + ". Please update your payment method.",
                    "{\"bookingId\":\"" + event.getBookingId() + "\"}",
                    null,
                    null
            );
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundSucceeded(RefundSucceededEvent event) {
        log.info("Processing RefundSucceededEvent in notification module: {}", event.getRefundId());

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            dispatcher.dispatch(
                    booking.getGuestId(),
                    NotificationCategory.PAYMENT,
                    "REFUND_PROCESSED",
                    "Refund Processed — $" + event.getAmount(),
                    "A refund of $" + event.getAmount() + " has been issued to your original payment method.",
                    "{\"refundId\":\"" + event.getRefundId() + "\",\"bookingId\":\"" + event.getBookingId() + "\"}",
                    null,
                    null
            );
        });
    }
}
