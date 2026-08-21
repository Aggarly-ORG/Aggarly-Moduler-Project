package com.luna.aggarly.review.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ReviewNotFoundException extends AggarlyException {
    public ReviewNotFoundException(UUID id) {
        super("Review not found: " + id, HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND");
    }
}
