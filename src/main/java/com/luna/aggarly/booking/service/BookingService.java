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
    List<BookingResponse> getMyBookings(UUID guestId, String statusFilter);
    List<BookingResponse> getHostBookings(UUID hostId);
    List<BookingResponse> getHostBookings(UUID hostId, String statusFilter);
    CancellationQuoteResponse getCancellationQuote(UUID bookingId, UUID guestId);
    BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, UUID guestId);
    void confirmBooking(UUID bookingId);
    void cancelDueToPaymentFailure(UUID bookingId, String reason);
    void expireUnconfirmedBookings(int timeoutMinutes);
    boolean hasCompletedStay(UUID guestId, UUID propertyId);
    String generateCalendarIcs(UUID bookingId, UUID userId);
    com.luna.aggarly.booking.dto.BookingInvoiceResponse getBookingInvoice(UUID bookingId, UUID userId);
    com.luna.aggarly.booking.dto.ArrivalDossierResponse getArrivalDossier(UUID bookingId, UUID userId);
    void sendHostResidentMessage(UUID bookingId, UUID senderId, String messageText);
}
