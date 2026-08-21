package com.luna.aggarly.cleaning.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class CleaningIssueNotFoundException extends AggarlyException {
    public CleaningIssueNotFoundException(UUID id) {
        super("Cleaning issue not found: " + id, HttpStatus.NOT_FOUND, "CLEANING_ISSUE_NOT_FOUND");
    }
}
