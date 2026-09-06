package com.luna.aggarly.vision.exception;

import com.luna.aggarly.common.exceptions.AggarlyException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an uploaded search reference image is out-of-domain
 * (e.g. footwear, apparel, vehicles, animals, memes) rather than a real estate or room scene.
 */
public class InvalidSearchImageException extends AggarlyException {

    public InvalidSearchImageException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_SEARCH_IMAGE");
    }

    public InvalidSearchImageException(String detectedSubject, String reason) {
        super(
                String.format("The uploaded image does not appear to be a property or room scene (detected: %s). %s",
                        detectedSubject != null ? detectedSubject : "non-property object",
                        reason != null ? reason : "Please upload a photo of a vacation home, room, terrace, or view."),
                HttpStatus.BAD_REQUEST,
                "INVALID_SEARCH_IMAGE"
        );
    }
}
