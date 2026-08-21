package com.luna.aggarly.vision.event;

import java.util.UUID;

public record PropertyDeletedEvent(
        UUID propertyId
) {}
