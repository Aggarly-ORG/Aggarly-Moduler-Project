package com.luna.aggarly.cleaning.dto.response;

import java.util.List;
import java.util.UUID;

public record CleaningChecklistResponse(
        UUID id,
        UUID cleaningTaskId,
        String roomName,
        int displayOrder,
        List<CleaningChecklistItemResponse> items
) {}
