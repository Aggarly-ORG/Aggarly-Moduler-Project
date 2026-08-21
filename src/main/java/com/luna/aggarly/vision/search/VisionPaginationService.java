package com.luna.aggarly.vision.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
public class VisionPaginationService {

    public record CursorData(int offset, float lastScore, UUID lastPropertyId) {}

    public String encodeCursor(int nextOffset, float lastScore, UUID lastPropertyId) {
        String raw = String.format("%d:%.4f:%s", nextOffset, lastScore, lastPropertyId != null ? lastPropertyId.toString() : "");
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public CursorData decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorData(0, Float.MAX_VALUE, null);
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            int offset = Integer.parseInt(parts[0]);
            float score = parts.length > 1 ? Float.parseFloat(parts[1]) : Float.MAX_VALUE;
            UUID propId = (parts.length > 2 && !parts[2].isBlank()) ? UUID.fromString(parts[2]) : null;
            return new CursorData(offset, score, propId);
        } catch (Exception e) {
            log.warn("Invalid pagination cursor token: {}", cursor);
            return new CursorData(0, Float.MAX_VALUE, null);
        }
    }
}
