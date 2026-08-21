package com.luna.aggarly.vision.exception;

public interface VisionExceptionPolicy {
    VisionRetryPolicy getRetryPolicy();
}
