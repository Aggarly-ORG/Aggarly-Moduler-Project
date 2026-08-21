package com.luna.aggarly.filestorage.exceptions;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

public class FileNotFoundException extends AggarlyException {
    public FileNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
    }
}
