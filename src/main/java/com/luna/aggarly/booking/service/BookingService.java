package com.luna.aggarly.booking.service;

import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.dto.CancellationQuoteResponse;
import com.luna.aggarly.booking.dto.CreateBookingRequest;

import java.util.List;
import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, UUID guestId);
    BookingResponse getById(UUID bookingId, UUID guestId);
    List<BookingResponse> getMyBookings(UUID guestId);
    CancellationQuoteResponse getCancellationQuote(UUID bookingId, UUID guestId);
    BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, UUID guestId);
    void confirmBooking(UUID bookingId);
    void cancelDueToPaymentFailure(UUID bookingId, String reason);
    boolean hasCompletedStay(UUID guestId, UUID propertyId);
}
