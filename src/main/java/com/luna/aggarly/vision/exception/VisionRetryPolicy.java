package com.luna.aggarly.vision.exception;

import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;

public enum VisionRetryPolicy {
    RETRYABLE,
    NON_RETRYABLE,
    DEAD_LETTER
}
