package com.luna.aggarly.booking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.dto.CancellationQuoteResponse;
import com.luna.aggarly.booking.dto.CreateBookingRequest;
import com.luna.aggarly.booking.engine.CancellationPolicyResolver;
import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import com.luna.aggarly.booking.entity.BookingStatusHistory;
import com.luna.aggarly.booking.event.BookingCancelledEvent;
import com.luna.aggarly.booking.event.BookingConfirmedEvent;
import com.luna.aggarly.booking.exceptions.BookingCreationFailedException;
import com.luna.aggarly.booking.exceptions.BookingNotFoundException;
import com.luna.aggarly.booking.exceptions.InvalidBookingStateException;
import com.luna.aggarly.booking.mapper.BookingMapper;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.booking.repository.BookingStatusHistoryRepository;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.payment.dto.PaymentIntentResponse;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.pricing.dto.PriceQuoteResponse;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.entity.enums.CancellationPolicyType;
import com.luna.aggarly.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final AvailabilityService availabilityService;
    private final PricingRuleService pricingRuleService;
    private final PaymentService paymentService;
    private final PropertyService propertyService;
    private final CancellationPolicyResolver cancellationPolicyResolver;
    private final BookingMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, UUID guestId) {
        log.info("Initiating booking for propertyId={}, guestId={}", request.propertyId(), guestId);
        PropertyResponse property = propertyService.getPropertyById(request.propertyId());

        if (request.guestCount() > property.maxGuests()) {
            throw new BookingCreationFailedException("Guest count exceeds maximum allowed for this property (" + property.maxGuests() + ")");
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .propertyId(request.propertyId())
                .guestId(guestId)
                .hostId(property.hostId())
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .guestCount(request.guestCount())
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.ZERO)
                .currency("USD")
                .priceBreakdownJson("{}")
                .couponCode(request.couponCode())
                .build());

        try {
            availabilityService.blockForBooking(request.propertyId(), booking.getId(),
                    request.checkIn(), request.checkOut());

            PriceQuoteResponse quote = pricingRuleService.getQuote(request.propertyId(),
                    request.checkIn(), request.checkOut(), request.couponCode(), guestId);

            booking.setTotalAmount(quote.total());
            booking.setPriceBreakdownJson(serializeQuote(quote));
            bookingRepository.save(booking);

            String idempotencyKey = "booking_payment_" + booking.getId();
            PaymentIntentResponse intent = paymentService.createPaymentIntent(
                    booking.getId(), guestId, quote.total(), booking.getCurrency(), idempotencyKey);

            recordTransition(booking.getId(), null, BookingStatus.PENDING_PAYMENT, "Booking created");

            return mapper.toResponse(booking, intent.getClientSecret());

        } catch (Exception ex) {
            log.error("Booking creation saga failed for bookingId={}, compensating...", booking.getId(), ex);
            try {
                availabilityService.releaseDates(request.propertyId(), booking.getId());
            } catch (Exception e) {
                log.error("Failed to release dates during compensation for bookingId={}", booking.getId(), e);
            }
            bookingRepository.delete(booking);
            throw new BookingCreationFailedException("Unable to create booking: " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public BookingResponse getById(UUID bookingId, UUID guestId) {
        Booking booking = (guestId != null)
                ? bookingRepository.findByIdAndGuestId(bookingId, guestId).orElseThrow(() -> new BookingNotFoundException(bookingId))
                : bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            try {
                com.luna.aggarly.payment.entity.enums.PaymentStatus ps = paymentService.getPaymentStatus(bookingId);
                if (ps == com.luna.aggarly.payment.entity.enums.PaymentStatus.SUCCEEDED) {
                    booking = bookingRepository.findById(bookingId).orElse(booking);
                }
            } catch (Exception e) {
                log.debug("No payment status to reconcile for bookingId={}", bookingId);
            }
        }
        return mapper.toResponse(booking, null);
    }

    @Override
    @Transactional
    public List<BookingResponse> getMyBookings(UUID guestId) {
        List<Booking> bookings = bookingRepository.findByGuestIdOrderByCheckInDesc(guestId);
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                try {
                    com.luna.aggarly.payment.entity.enums.PaymentStatus ps = paymentService.getPaymentStatus(b.getId());
                    if (ps == com.luna.aggarly.payment.entity.enums.PaymentStatus.SUCCEEDED) {
                        b.setStatus(BookingStatus.CONFIRMED);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        return bookings.stream()
                .map(booking -> mapper.toResponse(booking, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CancellationQuoteResponse getCancellationQuote(UUID bookingId, UUID guestId) {
        Booking booking = bookingRepository.findByIdAndGuestId(bookingId, guestId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        PropertyResponse property = propertyService.getPropertyById(booking.getPropertyId());
        CancellationPolicyType policyType = CancellationPolicyType.valueOf(property.cancellationPolicy().name());

        BigDecimal refundPercentage = cancellationPolicyResolver.resolveRefundPercentage(
                policyType, booking.getCheckIn(), LocalDate.now());

        BigDecimal refundAmount = booking.getTotalAmount()
                .multiply(refundPercentage)
                .divide(BigDecimal.valueOf(100));

        return new CancellationQuoteResponse(refundPercentage, refundAmount,
                policyType.name() + " policy applied");
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, UUID guestId) {
        log.info("Guest requested cancellation for bookingId={}", bookingId);
        Booking booking = bookingRepository.findByIdAndGuestId(bookingId, guestId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateException("Cannot cancel a booking in status " + booking.getStatus());
        }

        CancellationQuoteResponse quote = getCancellationQuote(bookingId, guestId);

        BookingStatus previousStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.reason());
        bookingRepository.save(booking);

        availabilityService.releaseDates(booking.getPropertyId(), booking.getId());

        recordTransition(booking.getId(), previousStatus, BookingStatus.CANCELLED, request.reason());

        if (quote.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
            eventPublisher.publishEvent(new BookingCancelledEvent(this, booking.getId(),
                    quote.refundAmount(), request.reason()));
        }

        return mapper.toResponse(booking, null);
    }

    @Override
    @Transactional
    public void confirmBooking(UUID bookingId) {
        log.info("Confirming bookingId={}", bookingId);
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                log.warn("Booking {} is in status {}, skipping confirmation", bookingId, booking.getStatus());
                return;
            }
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            recordTransition(booking.getId(), BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, "Payment succeeded");
            eventPublisher.publishEvent(new BookingConfirmedEvent(this, booking.getId()));
        });
    }

    @Override
    @Transactional
    public void cancelDueToPaymentFailure(UUID bookingId, String reason) {
        log.info("Cancelling bookingId={} due to payment failure: {}", bookingId, reason);
        bookingRepository.findById(bookingId).ifPresent(booking -> {
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                log.warn("Booking {} is in status {}, skipping payment failure cancellation", bookingId, booking.getStatus());
                return;
            }
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancellationReason(reason);
            bookingRepository.save(booking);
            availabilityService.releaseDates(booking.getPropertyId(), booking.getId());
            recordTransition(booking.getId(), BookingStatus.PENDING_PAYMENT, BookingStatus.CANCELLED, reason);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCompletedStay(UUID guestId, UUID propertyId) {
        return bookingRepository.existsByGuestIdAndPropertyIdAndStatus(guestId, propertyId, BookingStatus.COMPLETED);
    }

    private void recordTransition(UUID bookingId, BookingStatus from, BookingStatus to, String reason) {
        historyRepository.save(BookingStatusHistory.builder()
                .bookingId(bookingId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build());
    }

    private String serializeQuote(PriceQuoteResponse quote) {
        try {
            return objectMapper.writeValueAsString(quote);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PriceQuoteResponse", e);
            return "{}";
        }
    }
}
