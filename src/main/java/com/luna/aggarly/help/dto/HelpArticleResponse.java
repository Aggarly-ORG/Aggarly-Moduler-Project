package com.luna.aggarly.help.dto;

import java.time.Instant;
import java.util.UUID;

public record HelpArticleResponse(
        UUID id,
        String slug,
        String title,
        String category,
        String content,
        Instant createdAt
) {}