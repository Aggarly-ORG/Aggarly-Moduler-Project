package com.luna.aggarly.vision.exception;

import lombok.Getter;

@Getter
public class VisionInferenceException extends VisionPipelineException {

    public VisionInferenceException(String message) {
        super(message, VisionRetryPolicy.RETRYABLE);
    }

    public VisionInferenceException(String message, VisionRetryPolicy retryPolicy) {
        super(message, retryPolicy);
    }

    public VisionInferenceException(String message, Throwable cause, VisionRetryPolicy retryPolicy) {
        super(message, cause, retryPolicy);
    }
}
