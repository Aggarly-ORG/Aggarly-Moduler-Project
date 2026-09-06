package com.luna.aggarly.vision.service;

import com.luna.aggarly.vision.dto.PhotoTourWalkthroughResponse;

import java.util.UUID;

public interface PhotoTourSequencingService {

    /**
     * Generate a topologically ordered room-by-room photo tour walkthrough for a property.
     * Sequences scenes naturally: EXTERIOR -> LIVING_ROOM -> DINING -> KITCHEN -> BEDROOM -> BATHROOM -> BALCONY -> POOL -> VIEW -> WORKSPACE -> OTHER
     *
     * @param propertyId The target property UUID
     * @return Ordered photo tour walkthrough response
     */
    PhotoTourWalkthroughResponse generatePhotoTour(UUID propertyId);
}
