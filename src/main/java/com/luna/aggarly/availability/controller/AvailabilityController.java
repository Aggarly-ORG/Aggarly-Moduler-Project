package com.luna.aggarly.availability.controller;

import com.luna.aggarly.availability.dto.AvailabilityCalendarResponse;
import com.luna.aggarly.availability.dto.AvailabilityCheckResponse;
import com.luna.aggarly.availability.dto.BlockDatesRequest;
import com.luna.aggarly.availability.dto.BulkAvailabilityUpdateRequest;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller managing property calendar availability, blocking, and booking date checks.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/availability")
@RequiredArgsConstructor
@Tag(name = "Availability", description = "Property Calendar, Booking Locks & Availability Check APIs")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    @Operation(summary = "Get property availability calendar for date range (Public)")
    public ResponseEntity<ApiResponse<AvailabilityCalendarResponse>> getCalendar(
            @PathVariable UUID propertyId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        AvailabilityCalendarResponse response = availabilityService.getCalendar(propertyId, from, to);
        return ApiResponse.ok(response, "Availability calendar retrieved successfully").toResponseEntity();
    }

    @GetMapping("/check")
    @Operation(summary = "Check availability for check-in / check-out dates (Public)")
    public ResponseEntity<ApiResponse<AvailabilityCheckResponse>> checkAvailability(
            @PathVariable UUID propertyId,
            @RequestParam LocalDate checkIn,
            @RequestParam LocalDate checkOut) {
        AvailabilityCheckResponse response = availabilityService.checkAvailability(propertyId, checkIn, checkOut);
        return ApiResponse.ok(response, "Availability check completed").toResponseEntity();
    }

    @PutMapping
    @PreAuthorize("hasRole('HOST')")
    @Operation(summary = "Bulk update availability rules and prices (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> bulkUpdate(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody BulkAvailabilityUpdateRequest request) {
        availabilityService.bulkUpdate(propertyId, request);
        return ApiResponse.<Void>empty("Availability updated successfully").toResponseEntity();
    }

    @PostMapping("/block")
    @PreAuthorize("hasRole('HOST')")
    @Operation(summary = "Block dates on calendar (HOST only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> blockDates(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody BlockDatesRequest request) {
        availabilityService.blockDates(propertyId, request);
        return ApiResponse.<Void>empty("Dates blocked successfully").toResponseEntity();
    }
}
