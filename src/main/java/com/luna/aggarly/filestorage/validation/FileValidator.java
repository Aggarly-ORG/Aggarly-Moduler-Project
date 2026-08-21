package com.luna.aggarly.filestorage.validation;

import com.luna.aggarly.filestorage.exceptions.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator {
    @Value("${app.storage.max-file-size}")
    private long maxFileSize;
    @Value("${app.storage.allowed-types}")
    private List<String> allowedTypes;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new InvalidFileException("File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }
        log.info("{}",allowedTypes.size());
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new InvalidFileException("File type not allowed: " + contentType);
        }
    }
}
