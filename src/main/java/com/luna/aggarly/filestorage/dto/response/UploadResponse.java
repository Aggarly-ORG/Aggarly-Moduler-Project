package com.luna.aggarly.filestorage.dto.response;

import com.luna.aggarly.filestorage.entity.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record UploadResponse(
        UUID id,
        String objectKey,
        String contentType,
        Long size,
        Instant uploadedAt,
        FileStatus status
) {
}
