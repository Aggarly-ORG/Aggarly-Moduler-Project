package com.luna.aggarly.scheduler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.scheduler.entity.enums.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerCalculator {

    private final ObjectMapper objectMapper;

    public Instant calculateNextExecutionAt(
            TriggerType triggerType,
            String triggerConfigJson,
            Instant lastExecutionAt,
            String timezoneStr
    ) {
        if (triggerType == null) {
            return null;
        }

        ZoneId zone = resolveZone(timezoneStr);
        Instant now = Instant.now();
        ZonedDateTime nowZoned = now.atZone(zone);

        try {
            JsonNode config = objectMapper.readTree(triggerConfigJson != null ? triggerConfigJson : "{}");

            return switch (triggerType) {
                case ONCE -> calculateOnce(config, lastExecutionAt, zone);
                case DAILY -> calculateDaily(config, nowZoned, zone);
                case WEEKLY -> calculateWeekly(config, nowZoned, zone);
                case MONTHLY -> calculateMonthly(config, nowZoned, zone);
                case INTERVAL -> calculateInterval(config, lastExecutionAt, now);
                case EVENT, EVENT_OFFSET -> null; // Event triggers fire on incoming domain events
            };
        } catch (Exception ex) {
            log.error("Failed to compute next execution for triggerType={}: {}", triggerType, ex.getMessage(), ex);
            return null;
        }
    }

    private Instant calculateOnce(JsonNode config, Instant lastExecutionAt, ZoneId zone) {
        if (lastExecutionAt != null) {
            return null; // One-time schedule already executed
        }
        if (config.has("executeAt")) {
            String executeAtStr = config.get("executeAt").asText();
            try {
                return Instant.ofEpochSecond((long)Double.parseDouble(executeAtStr));
            } catch (Exception ignored) {
                LocalDateTime ldt = LocalDateTime.parse(executeAtStr);
                return ldt.atZone(zone).toInstant();
            }
        }
        return null;
    }

    private Instant calculateDaily(JsonNode config, ZonedDateTime nowZoned, ZoneId zone) {
        LocalTime time = parseTime(config.path("time").asText("09:00"));
        ZonedDateTime candidate = nowZoned.with(time);

        if (!candidate.isAfter(nowZoned)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private Instant calculateWeekly(JsonNode config, ZonedDateTime nowZoned, ZoneId zone) {
        LocalTime time = parseTime(config.path("time").asText("09:00"));
        List<DayOfWeek> days = new ArrayList<>();

        if (config.has("days") && config.get("days").isArray()) {
            for (JsonNode d : config.get("days")) {
                try {
                    days.add(DayOfWeek.valueOf(d.asText().toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {}
            }
        }
        if (days.isEmpty()) {
            days.add(DayOfWeek.MONDAY);
        }

        ZonedDateTime earliest = null;
        for (DayOfWeek targetDay : days) {
            ZonedDateTime candidate = nowZoned.with(TemporalAdjusters.nextOrSame(targetDay)).with(time);
            if (!candidate.isAfter(nowZoned)) {
                candidate = candidate.plusWeeks(1);
            }
            if (earliest == null || candidate.isBefore(earliest)) {
                earliest = candidate;
            }
        }

        return earliest != null ? earliest.toInstant() : null;
    }

    private Instant calculateMonthly(JsonNode config, ZonedDateTime nowZoned, ZoneId zone) {
        LocalTime time = parseTime(config.path("time").asText("09:00"));
        int targetDay = config.path("dayOfMonth").asInt(10);
        if (targetDay < 1) targetDay = 1;
        if (targetDay > 31) targetDay = 31;

        ZonedDateTime candidate = buildMonthlyCandidate(nowZoned, targetDay, time);
        if (!candidate.isAfter(nowZoned)) {
            candidate = buildMonthlyCandidate(nowZoned.plusMonths(1), targetDay, time);
        }

        return candidate.toInstant();
    }

    private ZonedDateTime buildMonthlyCandidate(ZonedDateTime base, int targetDay, LocalTime time) {
        int maxDaysInMonth = base.toLocalDate().lengthOfMonth();
        int actualDay = Math.min(targetDay, maxDaysInMonth);
        return base.withDayOfMonth(actualDay).with(time);
    }

    private Instant calculateInterval(JsonNode config, Instant lastExecutionAt, Instant now) {
        int every = config.path("every").asInt(1);
        String unit = config.path("unit").asText("DAYS").toUpperCase(Locale.ROOT);

        Duration duration = switch (unit) {
            case "MINUTES" -> Duration.ofMinutes(every);
            case "HOURS" -> Duration.ofHours(every);
            case "DAYS" -> Duration.ofDays(every);
            case "WEEKS" -> Duration.ofDays((long) every * 7);
            default -> Duration.ofDays(1);
        };

        Instant base = lastExecutionAt != null ? lastExecutionAt : now;
        Instant next = base.plus(duration);
        if (!next.isAfter(now)) {
            next = now.plus(duration);
        }
        return next;
    }

    private LocalTime parseTime(String timeStr) {
        try {
            if (timeStr != null && timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                return LocalTime.of(h, m);
            }
        } catch (Exception ignored) {}
        return LocalTime.of(9, 0);
    }

    private ZoneId resolveZone(String timezoneStr) {
        if (timezoneStr != null && !timezoneStr.isBlank()) {
            try {
                return ZoneId.of(timezoneStr);
            } catch (Exception ignored) {}
        }
        return ZoneId.of("UTC");
    }
}
