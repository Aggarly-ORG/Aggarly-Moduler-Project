package com.luna.aggarly.vision.exception;

import com.luna.aggarly.common.exceptions.AggarlyException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class VisionPipelineException extends AggarlyException implements VisionExceptionPolicy {

    private final VisionRetryPolicy retryPolicy;

    public VisionPipelineException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VISION_PIPELINE_ERROR");
        this.retryPolicy = VisionRetryPolicy.RETRYABLE;
    }

    public VisionPipelineException(String message, VisionRetryPolicy retryPolicy) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "VISION_PIPELINE_ERROR");
        this.retryPolicy = retryPolicy;
    }

    public VisionPipelineException(String message, Throwable cause, VisionRetryPolicy retryPolicy) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "VISION_PIPELINE_ERROR");
        this.retryPolicy = retryPolicy;
    }
}
