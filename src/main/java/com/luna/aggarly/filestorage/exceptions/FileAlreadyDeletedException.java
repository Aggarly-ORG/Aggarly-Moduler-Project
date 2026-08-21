package com.luna.aggarly.filestorage.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class FileAlreadyDeletedException extends AggarlyException {
    public FileAlreadyDeletedException(String message) {
        super(message, HttpStatus.GONE, "FILE_ALREADY_DELETED");
    }
}
