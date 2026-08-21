package com.luna.aggarly.availability.mapper;

import com.luna.aggarly.availability.dto.AvailabilityCalendarResponse;
import com.luna.aggarly.availability.dto.AvailabilitySlotResponse;
import com.luna.aggarly.availability.entity.AvailabilitySlot;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface AvailabilityMapper {

    default AvailabilitySlotResponse toResponse(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getPropertyId(),
                slot.getStartDate(),
                slot.getEndDate(),
                slot.isAvailable(),
                slot.getBlockReason() != null ? slot.getBlockReason().name() : null
        );
    }

    default AvailabilityCalendarResponse toCalendarResponse(UUID propertyId, LocalDate from, LocalDate to,
                                                             List<AvailabilitySlot> slots) {
        return new AvailabilityCalendarResponse(
                propertyId, from, to,
                slots.stream().map(this::toResponse).collect(Collectors.toList())
        );
    }
}
