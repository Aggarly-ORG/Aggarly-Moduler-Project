package com.luna.aggarly.booking.controller;

import com.luna.aggarly.booking.dto.BookingResponse;
import com.luna.aggarly.booking.dto.CancelBookingRequest;
import com.luna.aggarly.booking.dto.CancellationQuoteResponse;
import com.luna.aggarly.booking.dto.CreateBookingRequest;
import com.luna.aggarly.booking.service.BookingService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing booking reservations, status inquiries, and cancellation policies.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Reservation Creation, Lifecycle & Cancellation APIs")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a new booking reservation", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(request, principal.getUserId());
        return ApiResponse.created(response, "Booking reservation created successfully").toResponseEntity();
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking details by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BookingResponse>> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId) {
        BookingResponse response = bookingService.getById(bookingId, principal.getUserId());
        return ApiResponse.ok(response, "Booking details retrieved successfully").toResponseEntity();
    }

    @GetMapping
    @Operation(summary = "Get all bookings for the authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<BookingResponse> bookings = bookingService.getMyBookings(principal.getUserId());
        return ApiResponse.ok(bookings, "User bookings retrieved successfully").toResponseEntity();
    }

    @GetMapping("/{bookingId}/cancellation-quote")
    @Operation(summary = "Calculate refund quote prior to cancelling booking", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CancellationQuoteResponse>> getCancellationQuote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId) {
        CancellationQuoteResponse quote = bookingService.getCancellationQuote(bookingId, principal.getUserId());
        return ApiResponse.ok(quote, "Cancellation refund quote calculated").toResponseEntity();
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking reservation", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request) {
        BookingResponse response = bookingService.cancelBooking(bookingId, request, principal.getUserId());
        return ApiResponse.ok(response, "Booking cancelled successfully").toResponseEntity();
    }
}
