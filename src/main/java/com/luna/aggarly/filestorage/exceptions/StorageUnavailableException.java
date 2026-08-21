package com.luna.aggarly.filestorage.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class StorageUnavailableException extends AggarlyException {
    public StorageUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE");
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE");
    }
}
