package com.luna.aggarly.cleaning.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CleaningChecklistNotFoundException extends AggarlyException {
    public CleaningChecklistNotFoundException(UUID id) {
        super("Cleaning checklist item not found: " + id, HttpStatus.NOT_FOUND, "CLEANING_CHECKLIST_NOT_FOUND");
    }
}
