package com.luna.aggarly.availability.service.impl;

import com.luna.aggarly.availability.dto.*;
import com.luna.aggarly.availability.entity.AvailabilitySlot;
import com.luna.aggarly.availability.entity.BlockReason;
import com.luna.aggarly.availability.exceptions.AvailabilitySlotNotFoundException;
import com.luna.aggarly.availability.exceptions.DateRangeOverlapException;
import com.luna.aggarly.availability.mapper.AvailabilityMapper;
import com.luna.aggarly.availability.repository.AvailabilitySlotRepository;
import com.luna.aggarly.availability.service.AvailabilityService;
import com.luna.aggarly.availability.utils.DateRangeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private final AvailabilitySlotRepository slotRepository;
    private final AvailabilityMapper mapper;
    private final CacheManager cacheManager;
    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "property-calendar",
            key = "#propertyId + ':' + " +
                    "#from.year + ':' + #from.monthValue + ':' + " +
                    "#to.year + ':' + #to.monthValue"
    )
    public AvailabilityCalendarResponse getCalendar(
            UUID propertyId,
            LocalDate from,
            LocalDate to) {

        List<AvailabilitySlot> slots = slotRepository.findSlotsInRange(propertyId, from, to);
        return mapper.toCalendarResponse(propertyId, from, to, slots);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityCheckResponse checkAvailability(UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        DateRangeUtils.validate(checkIn, checkOut);
        boolean overlapping = slotRepository.existsOverlappingBlockedSlot(propertyId, checkIn, checkOut);
        return new AvailabilityCheckResponse(!overlapping, checkIn, checkOut,
                overlapping ? "Selected dates are not available" : null);
    }

    @Override
    @Transactional
    public void blockDates(UUID propertyId, BlockDatesRequest request) {
        DateRangeUtils.validate(request.checkIn(), request.checkOut());
        insertBlockedSlot(propertyId, request.checkIn(), request.checkOut(),
                BlockReason.valueOf(request.reason()), null);
        evictCalendarCache(propertyId,request.checkIn(),request.checkOut());
    }

    @Override
    @Transactional
    public void bulkUpdate(UUID propertyId, BulkAvailabilityUpdateRequest request) {
        request.ranges().forEach(range -> {
            DateRangeUtils.validate(range.checkIn(), range.checkOut());
            if (range.available()) {
                releaseRangeIfNotBooked(propertyId, range.checkIn(), range.checkOut());
            } else {
                insertBlockedSlot(propertyId, range.checkIn(), range.checkOut(),
                        BlockReason.HOST_BLOCKED, null);
            }
            evictCalendarCache(propertyId,range.checkIn(),range.checkOut());
        });
    }

    @Override
    @Transactional
    public AvailabilitySlotResponse blockForBooking(UUID propertyId, UUID bookingId,
                                                     LocalDate checkIn, LocalDate checkOut) {
        DateRangeUtils.validate(checkIn, checkOut);
        if (slotRepository.existsOverlappingBlockedSlot(propertyId, checkIn, checkOut)) {
            throw new DateRangeOverlapException("Selected dates are no longer available");
        }
        AvailabilitySlot slot = insertBlockedSlot(propertyId, checkIn, checkOut,
                BlockReason.BOOKED, bookingId);
        evictCalendarCache(propertyId,checkIn,checkOut);
        return mapper.toResponse(slot);
    }

    @Override
    @Transactional
    public void releaseDates(UUID propertyId, UUID bookingId) {
        AvailabilitySlot slot = slotRepository.
                findByPropertyIdAndBookingId(propertyId, bookingId).
                orElseThrow(()->new AvailabilitySlotNotFoundException(bookingId));
        slotRepository.delete(slot);
        evictCalendarCache(propertyId,slot.getStartDate(),slot.getEndDate());
    }

    private AvailabilitySlot insertBlockedSlot(UUID propertyId, LocalDate checkIn, LocalDate checkOut,
                                                BlockReason reason, UUID bookingId) {
        try {
            AvailabilitySlot slot = AvailabilitySlot.builder()
                    .propertyId(propertyId)
                    .startDate(checkIn)
                    .endDate(checkOut)
                    .available(false)
                    .blockReason(reason)
                    .bookingId(bookingId)
                    .build();
            slot =slotRepository.save(slot);
            evictCalendarCache(propertyId,checkIn,checkOut);
            return slot;
        } catch (DataIntegrityViolationException ex) {
            throw new DateRangeOverlapException("Selected dates were just taken by another request");
        }
    }

    private void releaseRangeIfNotBooked(UUID propertyId, LocalDate from, LocalDate to) {
        List<AvailabilitySlot> slots = slotRepository.findSlotsInRange(propertyId, from, to);
        List<AvailabilitySlot> slotsToDelete = slots.stream()
                .filter(s -> s.getBlockReason() != BlockReason.BOOKED)
                .toList();
        slotRepository.deleteAll(slotsToDelete);
        evictCalendarCache(propertyId,from,to);
    }
    private void evictCalendarCache(UUID propertyId, LocalDate from, LocalDate to) {

        Cache cache = cacheManager.getCache("property-calendar");

        if (cache == null) {
            return;
        }

        YearMonth current = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);

        while (!current.isAfter(end)) {

            String key = propertyId + ":" +
                    current.getYear() + ":" +
                    current.getMonthValue() + ":" +
                    current.getYear() + ":" +
                    current.getMonthValue();

            cache.evict(key);

            current = current.plusMonths(1);
        }
    }
}
