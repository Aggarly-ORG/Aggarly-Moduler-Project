package com.luna.aggarly.vision.dto;

import lombok.Builder;

/**
 * Represents an autonomous or assistive UI action emitted by Lumen
 * to update the Host Listing Wizard or Sanctuary Management form.
 */
@Builder
public record UiCommandDto(
        String type,        // "CLICK_AMENITY", "UPDATE_FIELD", "SCROLL_TO", "SUGGEST_CONTENT"
        String target,      // Amenity UUID, DOM selector, or form field key ("bedrooms", "bathrooms", "title", etc.)
        Object value,       // Value or label payload
        String description  // Human-readable rationale for host feedback
) {}
