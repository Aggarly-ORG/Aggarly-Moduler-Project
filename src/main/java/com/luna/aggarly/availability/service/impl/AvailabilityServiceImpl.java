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
    private final com.luna.aggarly.property.repository.PropertyRepository propertyRepository;
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
        LocalDate checkIn = request.checkIn();
        LocalDate checkOut = request.checkOut();
        if (checkIn != null && checkOut != null && checkIn.equals(checkOut)) {
            checkOut = checkIn.plusDays(1);
        }
        DateRangeUtils.validate(checkIn, checkOut);

        BlockReason reason = BlockReason.HOST_BLOCKED;
        if (request.reason() != null && !request.reason().isBlank()) {
            String raw = request.reason().trim().toUpperCase();
            try {
                reason = BlockReason.valueOf(raw);
            } catch (IllegalArgumentException ex) {
                if (raw.contains("MAINTEN") || raw.contains("RENOVAT") || raw.contains("REPAIR")) {
                    reason = BlockReason.MAINTENANCE;
                } else if (raw.contains("BOOK")) {
                    reason = BlockReason.BOOKED;
                } else {
                    reason = BlockReason.HOST_BLOCKED;
                }
            }
        }

        releaseRangeIfNotBooked(propertyId, checkIn, checkOut);
        insertBlockedSlot(propertyId, checkIn, checkOut, reason, null);
        evictCalendarCache(propertyId, checkIn, checkOut);
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

    @Override
    @Transactional(readOnly = true)
    public MultiSanctuaryCalendarResponse getMultiCalendar(List<UUID> propertyIds, LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from.plusDays(30);

        List<MultiSanctuaryCalendarResponse.SanctuaryCalendarGrid> grids = new java.util.ArrayList<>();

        for (UUID propId : propertyIds) {
            com.luna.aggarly.property.entity.Property prop = propertyRepository.findById(propId).orElse(null);
            String title = prop != null ? prop.getTitle() : "Sanctuary " + propId.toString().substring(0, 6);
            java.math.BigDecimal basePrice = (prop != null && prop.getBasePricePerNight() != null)
                    ? prop.getBasePricePerNight()
                    : java.math.BigDecimal.valueOf(250);

            List<AvailabilitySlot> blockedSlots = slotRepository.findSlotsInRange(propId, from, to);
            List<MultiSanctuaryCalendarResponse.DailyCalendarCell> dayCells = new java.util.ArrayList<>();

            LocalDate curr = from;
            while (!curr.isAfter(to)) {
                LocalDate day = curr;
                boolean isBlocked = blockedSlots.stream().anyMatch(s ->
                        !day.isBefore(s.getStartDate()) && !day.isAfter(s.getEndDate()) && !s.isAvailable());

                int moonIllum = com.luna.aggarly.common.util.CelestialEphemerisCalculator.calculateMoonIlluminationPercent(day);
                String moonPhase = com.luna.aggarly.common.util.CelestialEphemerisCalculator.calculateMoonPhaseName(day);
                boolean lumenActive = moonIllum >= 50;

                java.math.BigDecimal price = basePrice;
                if (lumenActive) {
                    price = price.multiply(java.math.BigDecimal.valueOf(1.18)).setScale(2, java.math.RoundingMode.HALF_UP);
                }

                dayCells.add(new MultiSanctuaryCalendarResponse.DailyCalendarCell(
                        day,
                        !isBlocked,
                        price,
                        moonIllum,
                        moonPhase,
                        lumenActive
                ));

                curr = curr.plusDays(1);
            }

            grids.add(new MultiSanctuaryCalendarResponse.SanctuaryCalendarGrid(
                    propId,
                    title,
                    dayCells
            ));
        }

        return new MultiSanctuaryCalendarResponse(from, to, grids);
    }
}
