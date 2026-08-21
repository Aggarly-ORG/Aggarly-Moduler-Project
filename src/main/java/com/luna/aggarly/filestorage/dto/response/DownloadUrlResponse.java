package com.luna.aggarly.filestorage.dto.response;

import java.time.Instant;

public record DownloadUrlResponse(
        String url,
        Instant expiresAt
) {
}
