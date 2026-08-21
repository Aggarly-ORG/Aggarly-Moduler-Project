package com.luna.aggarly.vision.exception;

import lombok.Getter;

@Getter
public class QdrantOperationException extends VisionPipelineException {

    public QdrantOperationException(String message) {
        super(message, VisionRetryPolicy.RETRYABLE);
    }

    public QdrantOperationException(String message, Throwable cause) {
        super(message, cause, VisionRetryPolicy.RETRYABLE);
    }
}
