package com.luna.aggarly.chat.dto.block;

import java.util.Map;

public record ActionChip(
        String label,
        String actionType,
        Map<String, Object> parameters
) {
    public static ActionChip quickReply(String label, String prompt) {
        return new ActionChip(label, "QUICK_REPLY", Map.of("prompt", prompt != null ? prompt : label));
    }

    public static ActionChip datePicker(String propertyId) {
        return new ActionChip("📅 Check Dates", "DATE_PICKER", Map.of("propertyId", propertyId));
    }

    public static ActionChip photoTour(String propertyId) {
        return new ActionChip("📸 Spatial Tour", "PHOTO_TOUR", Map.of("propertyId", propertyId));
    }

    public static ActionChip scheduleVisit(String propertyId) {
        return new ActionChip("🤝 Schedule Visit", "SCHEDULE_VISIT", Map.of("propertyId", propertyId));
    }

    public static ActionChip visualSearch(String query) {
        return new ActionChip("✨ Visual Match", "VISUAL_SEARCH", Map.of("query", query != null ? query : ""));
    }
}
