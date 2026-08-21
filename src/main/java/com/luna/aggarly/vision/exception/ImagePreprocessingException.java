package com.luna.aggarly.vision.exception;

import lombok.Getter;

@Getter
public class ImagePreprocessingException extends VisionPipelineException {

    public ImagePreprocessingException(String message, VisionRetryPolicy retryPolicy) {
        super(message, retryPolicy);
    }

    public ImagePreprocessingException(String message, Throwable cause, VisionRetryPolicy retryPolicy) {
        super(message, cause, retryPolicy);
    }
}
