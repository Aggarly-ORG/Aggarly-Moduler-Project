package com.luna.aggarly.chat.dto.block;

import java.util.Map;

public record ChatUiBlock(
        ChatBlockType type,
        Map<String, Object> data
) {
    public static ChatUiBlock propertyCard(Map<String, Object> propertyData) {
        return new ChatUiBlock(ChatBlockType.PROPERTY_CARD, propertyData);
    }

    public static ChatUiBlock priceBreakdown(Map<String, Object> priceData) {
        return new ChatUiBlock(ChatBlockType.PRICE_BREAKDOWN, priceData);
    }

    public static ChatUiBlock photoTour(Map<String, Object> tourData) {
        return new ChatUiBlock(ChatBlockType.PHOTO_TOUR_PREVIEW, tourData);
    }

    public static ChatUiBlock scheduleConfirmation(Map<String, Object> scheduleData) {
        return new ChatUiBlock(ChatBlockType.SCHEDULE_CONFIRMATION, scheduleData);
    }

    public static ChatUiBlock actionChips(Object chips) {
        return new ChatUiBlock(ChatBlockType.ACTION_CHIPS, Map.of("chips", chips));
    }
}
