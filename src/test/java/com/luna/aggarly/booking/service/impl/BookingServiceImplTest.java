package com.luna.aggarly.booking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.dto.CreateBookingRequest;
import com.luna.aggarly.booking.engine.CancellationPolicyResolver;
import com.luna.aggarly.booking.entity.Booking;
import com.luna.aggarly.booking.entity.BookingStatus;
import com.luna.aggarly.booking.exceptions.BookingCreationFailedException;
import com.luna.aggarly.booking.exceptions.InvalidBookingStateException;
import com.luna.aggarly.booking.mapper.BookingMapper;
import com.luna.aggarly.booking.repository.BookingRepository;
import com.luna.aggarly.booking.repository.BookingStatusHistoryRepository;
import com.luna.aggarly.payment.service.PaymentService;
import com.luna.aggarly.pricing.service.PricingRuleService;
import com.luna.aggarly.property.dto.response.PropertyResponse;
import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.service.PropertyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingStatusHistoryRepository historyRepository;
    @Mock
    private AvailabilityService availabilityService;
    @Mock
    private PricingRuleService pricingRuleService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PropertyService propertyService;
    @Mock
    private CancellationPolicyResolver cancellationPolicyResolver;
    @Mock
    private BookingMapper mapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Test
    void createBooking_CompensatesOnPricingFailure() {
        UUID propertyId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();

        PropertyResponse property = new PropertyResponse(
                propertyId, hostId, "Title", "Desc", null, 4, 2, 1,
                BigDecimal.valueOf(100), CancellationPolicy.FLEXIBLE, null, null, null, null, 0, null, null, null
        );

        when(propertyService.getPropertyById(propertyId)).thenReturn(property);

        Booking booking = Booking.builder()
                .propertyId(propertyId)
                .guestId(guestId)
                .hostId(hostId)
                .checkIn(LocalDate.now().plusDays(1))
                .checkOut(LocalDate.now().plusDays(3))
                .guestCount(2)
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.ZERO)
                .currency("USD")
                .priceBreakdownJson("{}")
                .build();
        booking.setId(UUID.randomUUID());

        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);
        when(pricingRuleService.getQuote(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Pricing service failure"));

        CreateBookingRequest request = new CreateBookingRequest(
                propertyId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 2, null
        );

        assertThrows(BookingCreationFailedException.class, () -> bookingService.createBooking(request, guestId));

        verify(availabilityService).releaseDates(propertyId, booking.getId());
        verify(bookingRepository).delete(booking);
    }

    @Test
    void cancelBooking_ThrowsIfAlreadyCancelledOrCompleted() {
        UUID bookingId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setGuestId(guestId);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findByIdAndGuestId(bookingId, guestId)).thenReturn(Optional.of(booking));

        CancelBookingRequest request = new CancelBookingRequest("Reason");

        assertThrows(InvalidBookingStateException.class, () -> bookingService.cancelBooking(bookingId, request, guestId));
    }
}
