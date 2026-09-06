package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import com.luna.aggarly.common.expression.spi.ExpressionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Component
@ExpressionFunctionLibrary(value = "date", prefix = "DateUtils")
public class DateMathFunctionLibrary {

    @ExpressionFunction(value = "now", description = "Current UTC timestamp in ISO-8601")
    public String now() {
        return Instant.now().toString();
    }

    @ExpressionFunction(value = "today", description = "Current date in YYYY-MM-DD format")
    public String today(ExpressionContext context) {
        ZoneId zone = getZone(context);
        return LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @ExpressionFunction(value = "current_time", description = "Current time in HH:mm:ss format")
    public String currentTime(ExpressionContext context) {
        ZoneId zone = getZone(context);
        return LocalTime.now(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @ExpressionFunction(value = "date_add", description = "Adds an amount of units (DAYS, HOURS, MINUTES, WEEKS, MONTHS, YEARS) to a date")
    public String dateAdd(String dateStr, long amount, String unitStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            ChronoUnit unit = parseUnit(unitStr);
            String trimmed = dateStr.trim();
            if (trimmed.length() == 10) {
                LocalDate d = LocalDate.parse(trimmed);
                return d.plus(amount, unit).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                Instant inst = Instant.parse(trimmed);
                return inst.plus(amount, unit).toString();
            }
        } catch (Exception ex) {
            log.debug("date_add error for '{}': {}", dateStr, ex.getMessage());
            return dateStr;
        }
    }

    @ExpressionFunction(value = "date_sub", description = "Subtracts an amount of units from a date")
    public String dateSub(String dateStr, long amount, String unitStr) {
        return dateAdd(dateStr, -amount, unitStr);
    }

    @ExpressionFunction(value = "diff_days", description = "Calculates difference in days between two dates (date2 - date1)")
    public long diffDays(String date1Str, String date2Str) {
        try {
            LocalDate d1 = parseToLocalDate(date1Str);
            LocalDate d2 = parseToLocalDate(date2Str);
            return ChronoUnit.DAYS.between(d1, d2);
        } catch (Exception ex) {
            return 0L;
        }
    }

    @ExpressionFunction(value = "diff_hours", description = "Calculates difference in hours between two ISO timestamps")
    public long diffHours(String time1Str, String time2Str) {
        try {
            Instant t1 = Instant.parse(time1Str);
            Instant t2 = Instant.parse(time2Str);
            return ChronoUnit.HOURS.between(t1, t2);
        } catch (Exception ex) {
            return 0L;
        }
    }

    @ExpressionFunction(value = "format_date", description = "Formats a date string into a specified pattern (e.g. 'MMMM d, yyyy')")
    public String formatDate(String dateStr, String pattern, ExpressionContext context) {
        if (dateStr == null || dateStr.isBlank()) return "";
        try {
            String pat = (pattern != null && !pattern.isBlank()) ? pattern : "yyyy-MM-dd";
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pat);
            String trimmed = dateStr.trim();
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed).format(dtf);
            } else {
                Instant inst = Instant.parse(trimmed);
                return dtf.format(inst.atZone(getZone(context)));
            }
        } catch (Exception ex) {
            return dateStr;
        }
    }

    @ExpressionFunction(value = "start_of_week", description = "Returns Monday of the week for given date or today")
    public String startOfWeek(String dateStr, ExpressionContext context) {
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? parseToLocalDate(dateStr) : LocalDate.now(getZone(context));
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @ExpressionFunction(value = "end_of_week", description = "Returns Sunday of the week for given date or today")
    public String endOfWeek(String dateStr, ExpressionContext context) {
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? parseToLocalDate(dateStr) : LocalDate.now(getZone(context));
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @ExpressionFunction(value = "start_of_month", aliases = {"monthStart"}, description = "First day of month (YYYY-MM-01)")
    public String startOfMonth(String dateStr, ExpressionContext context) {
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? parseToLocalDate(dateStr) : LocalDate.now(getZone(context));
        return date.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @ExpressionFunction(value = "end_of_month", aliases = {"monthEnd"}, description = "Last day of month")
    public String endOfMonth(String dateStr, ExpressionContext context) {
        LocalDate date = (dateStr != null && !dateStr.isBlank()) ? parseToLocalDate(dateStr) : LocalDate.now(getZone(context));
        return date.withDayOfMonth(date.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @ExpressionFunction(value = "is_weekend", description = "Checks if date falls on Saturday or Sunday")
    public boolean isWeekend(String dateStr) {
        try {
            LocalDate d = parseToLocalDate(dateStr);
            return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        } catch (Exception ex) {
            return false;
        }
    }

    @ExpressionFunction(value = "is_past", description = "Checks if date is before today")
    public boolean isPast(String dateStr, ExpressionContext context) {
        try {
            LocalDate d = parseToLocalDate(dateStr);
            return d.isBefore(LocalDate.now(getZone(context)));
        } catch (Exception ex) {
            return false;
        }
    }

    @ExpressionFunction(value = "is_future", description = "Checks if date is after today")
    public boolean isFuture(String dateStr, ExpressionContext context) {
        try {
            LocalDate d = parseToLocalDate(dateStr);
            return d.isAfter(LocalDate.now(getZone(context)));
        } catch (Exception ex) {
            return false;
        }
    }

    @ExpressionFunction(value = "day_of_week", description = "Returns name of the day (e.g. MONDAY)")
    public String dayOfWeek(String dateStr) {
        try {
            return parseToLocalDate(dateStr).getDayOfWeek().name();
        } catch (Exception ex) {
            return "";
        }
    }

    private LocalDate parseToLocalDate(String str) {
        if (str == null || str.isBlank()) return LocalDate.now();
        String trimmed = str.trim();
        if (trimmed.length() >= 10) {
            return LocalDate.parse(trimmed.substring(0, 10));
        }
        return LocalDate.now();
    }

    private ChronoUnit parseUnit(String unitStr) {
        if (unitStr == null) return ChronoUnit.DAYS;
        return switch (unitStr.trim().toUpperCase()) {
            case "HOURS", "HOUR", "H" -> ChronoUnit.HOURS;
            case "MINUTES", "MINUTE", "M", "MIN" -> ChronoUnit.MINUTES;
            case "SECONDS", "SECOND", "S", "SEC" -> ChronoUnit.SECONDS;
            case "WEEKS", "WEEK", "W" -> ChronoUnit.WEEKS;
            case "MONTHS", "MONTH" -> ChronoUnit.MONTHS;
            case "YEARS", "YEAR", "Y" -> ChronoUnit.YEARS;
            default -> ChronoUnit.DAYS;
        };
    }

    private ZoneId getZone(ExpressionContext context) {
        if (context != null && context.timezone() != null) {
            try {
                return ZoneId.of(context.timezone());
            } catch (Exception ignored) {}
        }
        return ZoneId.of("UTC");
    }
}
