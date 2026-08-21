package com.luna.aggarly.filestorage.dto.response;

import com.luna.aggarly.filestorage.entity.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(
        UUID id,
        String objectKey,
        String bucket,
        String originalFilename,
        String contentType,
        String extension,
        Long size,
        String checksum,
        FileStatus status,
        UUID ownerId,
        Instant uploadedAt,
        Instant activatedAt,
        Instant deletedAt
) {
}
