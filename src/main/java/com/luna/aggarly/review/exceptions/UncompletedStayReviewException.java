package com.luna.aggarly.review.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class UncompletedStayReviewException extends AggarlyException {
    public UncompletedStayReviewException() {
        super("You can only review properties where you have a completed stay", HttpStatus.FORBIDDEN, "UNCOMPLETED_STAY_REVIEW");
    }
}
