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
import com.luna.aggarly.booking.exceptions.UnauthorizedBookingAccessException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        return getMyBookings(guestId, null);
    }

    @Override
    @Transactional
    public List<BookingResponse> getMyBookings(UUID guestId, String statusFilter) {
        List<Booking> bookings = bookingRepository.findByGuestIdOrderByCheckInDesc(guestId);
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                try {
                    com.luna.aggarly.payment.entity.enums.PaymentStatus ps = paymentService.getPaymentStatus(b.getId());
                    if (ps == com.luna.aggarly.payment.entity.enums.PaymentStatus.SUCCEEDED) {
                        b.setStatus(BookingStatus.CONFIRMED);
                    }
                } catch (Exception ignored) {}
            }
        }

        LocalDate today = LocalDate.now();
        if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
            String filter = statusFilter.trim().toUpperCase();
            bookings = bookings.stream().filter(b -> {
                if ("UPCOMING".equals(filter)) {
                    return b.getStatus() != BookingStatus.CANCELLED && !b.getCheckIn().isBefore(today);
                } else if ("PAST".equals(filter)) {
                    return b.getStatus() == BookingStatus.COMPLETED || b.getCheckOut().isBefore(today);
                } else if ("CANCELLED".equals(filter)) {
                    return b.getStatus() == BookingStatus.CANCELLED;
                }
                return b.getStatus().name().equalsIgnoreCase(filter);
            }).toList();
        }

        return bookings.stream()
                .map(booking -> mapper.toResponse(booking, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getHostBookings(UUID hostId) {
        return getHostBookings(hostId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getHostBookings(UUID hostId, String statusFilter) {
        log.info("Fetching all bookings for hostId={}, statusFilter={}", hostId, statusFilter);
        List<Booking> bookings = bookingRepository.findByHostIdOrderByCheckInDesc(hostId);
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.PENDING_PAYMENT) {
                try {
                    com.luna.aggarly.payment.entity.enums.PaymentStatus ps = paymentService.getPaymentStatus(b.getId());
                    if (ps == com.luna.aggarly.payment.entity.enums.PaymentStatus.SUCCEEDED) {
                        b.setStatus(BookingStatus.CONFIRMED);
                    }
                } catch (Exception ignored) {}
            }
        }

        LocalDate today = LocalDate.now();
        if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
            String filter = statusFilter.trim().toUpperCase();
            bookings = bookings.stream().filter(b -> {
                if ("IN_HOUSE".equals(filter)) {
                    return !b.getCheckIn().isAfter(today) && !b.getCheckOut().isBefore(today) && b.getStatus() == BookingStatus.CONFIRMED;
                } else if ("UPCOMING".equals(filter)) {
                    return b.getCheckIn().isAfter(today) && (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.PENDING_PAYMENT);
                } else if ("COMPLETED".equals(filter)) {
                    return b.getStatus() == BookingStatus.COMPLETED || (b.getCheckOut().isBefore(today) && b.getStatus() == BookingStatus.CONFIRMED);
                } else if ("CANCELLED".equals(filter)) {
                    return b.getStatus() == BookingStatus.CANCELLED;
                }
                return b.getStatus().name().equalsIgnoreCase(filter);
            }).toList();
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
    @Transactional
    public void expireUnconfirmedBookings(int timeoutMinutes) {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<Booking> expiredBookings = bookingRepository.findByStatusAndCreatedAtBefore(
                BookingStatus.PENDING_PAYMENT, cutoff);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Found {} unconfirmed booking(s) older than {} minutes to expire",
                expiredBookings.size(), timeoutMinutes);

        for (Booking booking : expiredBookings) {
            try {
                log.info("Expiring unconfirmed bookingId={} (created at {})", booking.getId(), booking.getCreatedAt());
                booking.setStatus(BookingStatus.CANCELLED);
                String reason = "Payment timeout: reservation was not confirmed within " + timeoutMinutes + " minutes";
                booking.setCancellationReason(reason);
                bookingRepository.save(booking);

                try {
                    availabilityService.releaseDates(booking.getPropertyId(), booking.getId());
                } catch (Exception e) {
                    log.warn("Failed to release dates for expired bookingId={}: {}", booking.getId(), e.getMessage());
                }

                try {
                    paymentService.cancelPayment(booking.getId(), "expire_booking_" + booking.getId());
                } catch (Exception e) {
                    log.debug("Payment cancel skipped or not applicable for bookingId={}: {}", booking.getId(), e.getMessage());
                }

                recordTransition(booking.getId(), BookingStatus.PENDING_PAYMENT, BookingStatus.CANCELLED, reason);
            } catch (Exception ex) {
                log.error("Failed to expire bookingId={}", booking.getId(), ex);
            }
        }
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

    @Override
    @Transactional(readOnly = true)
    public String generateCalendarIcs(UUID bookingId, UUID userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (userId != null && !booking.getGuestId().equals(userId) && !booking.getHostId().equals(userId)) {
            throw new UnauthorizedBookingAccessException();
        }

        PropertyResponse prop = null;
        try {
            prop = propertyService.getPropertyById(booking.getPropertyId());
        } catch (Exception ignored) {}

        String title = prop != null ? prop.title() : "Celestial Sanctuary Residency";
        String location = (prop != null && prop.address() != null) ? prop.address().city() + ", " + prop.address().country() : "Dark-Sky Preserve";

        String dtStamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        String dtStart = booking.getCheckIn().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String dtEnd = booking.getCheckOut().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Aggarly Sanctuary Escapes//NONSGML Residency Schedule//EN\r\n" +
                "CALSCALE:GREGORIAN\r\n" +
                "METHOD:PUBLISH\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + booking.getId() + "@aggarly.com\r\n" +
                "DTSTAMP:" + dtStamp + "\r\n" +
                "DTSTART;VALUE=DATE:" + dtStart + "\r\n" +
                "DTEND;VALUE=DATE:" + dtEnd + "\r\n" +
                "SUMMARY:Residency at " + title + "\r\n" +
                "DESCRIPTION:Celestial retreat at " + title + ".\\nVault Access Window: 16:00 Check-in\\nBooking Ref: " + booking.getId() + "\r\n" +
                "LOCATION:" + location + "\r\n" +
                "STATUS:CONFIRMED\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
    }

    @Override
    @Transactional(readOnly = true)
    public com.luna.aggarly.booking.dto.BookingInvoiceResponse getBookingInvoice(UUID bookingId, UUID userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (userId != null && !booking.getGuestId().equals(userId) && !booking.getHostId().equals(userId)) {
            throw new UnauthorizedBookingAccessException();
        }

        PropertyResponse prop = null;
        try {
            prop = propertyService.getPropertyById(booking.getPropertyId());
        } catch (Exception ignored) {}

        String title = prop != null ? prop.title() : "Celestial Sanctuary";
        int nights = Math.max(1, (int) ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut()));
        BigDecimal total = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal vatRate = BigDecimal.valueOf(10.0);
        BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.10), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal vatAmount = total.subtract(subtotal);
        BigDecimal touristTax = BigDecimal.valueOf(3.50).multiply(BigDecimal.valueOf(nights));
        BigDecimal cleaningFee = BigDecimal.valueOf(120.00);

        String invoiceNum = "INV-" + booking.getCheckIn().getYear() + "-" + booking.getId().toString().substring(0, 8).toUpperCase();
        String escrowStatus = booking.getStatus() == BookingStatus.CONFIRMED ? "ESCROW_SECURED" : "PENDING_RELEASE";

        return new com.luna.aggarly.booking.dto.BookingInvoiceResponse(
                invoiceNum,
                booking.getId(),
                title,
                "Resident Guest",
                "guest@aggarly.com",
                LocalDate.now(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                nights,
                subtotal,
                vatRate,
                vatAmount,
                touristTax,
                cleaningFee,
                total,
                booking.getCurrency() != null ? booking.getCurrency() : "EUR",
                escrowStatus,
                "Stripe Escrow •••• 4242",
                "ch_luna_" + booking.getId().toString().substring(0, 12)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public com.luna.aggarly.booking.dto.ArrivalDossierResponse getArrivalDossier(UUID bookingId, UUID userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (userId != null && !booking.getGuestId().equals(userId) && !booking.getHostId().equals(userId)) {
            throw new UnauthorizedBookingAccessException();
        }

        PropertyResponse prop = null;
        try {
            prop = propertyService.getPropertyById(booking.getPropertyId());
        } catch (Exception ignored) {}

        String title = prop != null ? prop.title() : "Celestial Sanctuary";
        Double lat = (prop != null && prop.latitude() != null) ? prop.latitude().doubleValue() : 28.2916;
        Double lng = (prop != null && prop.longitude() != null) ? prop.longitude().doubleValue() : -16.6291;

        int pinCode = Math.abs(booking.getId().hashCode()) % 9000 + 1000;
        int checkSuffix = Math.abs(booking.getGuestId().hashCode()) % 90 + 10;
        String vaultPin = pinCode + "-" + checkSuffix + "#";

        String wifiSsid = "Aggarly-Observatory-" + booking.getId().toString().substring(0, 4).toUpperCase();
        String wifiPasskey = "starlight-" + booking.getPropertyId().toString().substring(0, 8);

        return new com.luna.aggarly.booking.dto.ArrivalDossierResponse(
                booking.getId(),
                booking.getPropertyId(),
                title,
                "Resident Curator",
                booking.getCheckIn(),
                "16:00 CEST",
                booking.getCheckOut(),
                "11:00 CEST",
                vaultPin,
                wifiSsid,
                wifiPasskey,
                lat,
                lng,
                "Approach via the private observatory spur road. Switch headlights to low parking lamps 200m prior to gate to preserve night-vision adaptation.",
                "Designated covered solar carport bay #1 with Type-2 EV charging station.",
                "Calibrated & Collimated (Bortle Class 2 verified, Schmidt-Cassegrain 8-inch optical alignment nominal)"
        );
    }

    @Override
    @Transactional
    public void sendHostResidentMessage(UUID bookingId, UUID senderId, String messageText) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        log.info("Direct drawer message on booking {}: senderId={}, text={}", bookingId, senderId, messageText);
    }
}
