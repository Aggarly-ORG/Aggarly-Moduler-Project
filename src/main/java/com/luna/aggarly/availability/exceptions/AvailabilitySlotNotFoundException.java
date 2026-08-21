package com.luna.aggarly.availability.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AvailabilitySlotNotFoundException extends AggarlyException {
    public AvailabilitySlotNotFoundException(UUID id) {
        super("Availability slot not found: " + id, HttpStatus.NOT_FOUND, "AVAILABILITY_SLOT_NOT_FOUND");
    }
}
