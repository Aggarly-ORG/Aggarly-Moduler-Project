package com.luna.aggarly.availability.service;

import com.luna.aggarly.availability.dto.*;

import java.time.LocalDate;
import java.util.UUID;

public interface AvailabilityService {

    AvailabilityCalendarResponse getCalendar(UUID propertyId, LocalDate from, LocalDate to);

    AvailabilityCheckResponse checkAvailability(UUID propertyId, LocalDate checkIn, LocalDate checkOut);

    void blockDates(UUID propertyId, BlockDatesRequest request);

    void bulkUpdate(UUID propertyId, BulkAvailabilityUpdateRequest request);

    void releaseDates(UUID propertyId, UUID bookingId);

    AvailabilitySlotResponse blockForBooking(UUID propertyId, UUID bookingId, LocalDate checkIn, LocalDate checkOut);
}
