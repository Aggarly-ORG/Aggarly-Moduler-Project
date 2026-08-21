package com.luna.aggarly.filestorage.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class FileStorageException extends AggarlyException {
    public FileStorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR");
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR");
    }
}
