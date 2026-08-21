package com.luna.aggarly.cleaning.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CleaningTaskNotFoundException extends AggarlyException {
    public CleaningTaskNotFoundException(UUID id) {
        super("Cleaning task not found: " + id, HttpStatus.NOT_FOUND, "CLEANING_TASK_NOT_FOUND");
    }
}
