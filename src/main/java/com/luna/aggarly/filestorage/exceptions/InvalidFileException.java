package com.luna.aggarly.filestorage.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class InvalidFileException extends AggarlyException {
    public InvalidFileException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_FILE");
    }
}
