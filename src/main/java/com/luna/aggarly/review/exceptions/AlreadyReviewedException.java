package com.luna.aggarly.review.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AlreadyReviewedException extends AggarlyException {
    public AlreadyReviewedException(UUID bookingId) {
        super("A review has already been submitted for booking: " + bookingId, HttpStatus.CONFLICT, "ALREADY_REVIEWED");
    }
}
