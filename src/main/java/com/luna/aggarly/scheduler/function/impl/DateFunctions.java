package com.luna.aggarly.scheduler.function.impl;

import com.luna.aggarly.scheduler.function.WorkflowFunction;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DateFunctions {

    @Component
    public static class TodayFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.today";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            ZoneId zone = getZone(context);
            return LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @Component
    public static class CurrentTimeFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.currentTime";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            return context.executionTime().toString();
        }
    }

    @Component
    public static class MonthStartFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.monthStart";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            ZoneId zone = getZone(context);
            LocalDate now = LocalDate.now(zone);
            return now.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @Component
    public static class MonthEndFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.monthEnd";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            ZoneId zone = getZone(context);
            LocalDate now = LocalDate.now(zone);
            return now.withDayOfMonth(now.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @Component
    public static class PreviousMonthStartFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.previousMonthStart";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            ZoneId zone = getZone(context);
            LocalDate prevMonth = LocalDate.now(zone).minusMonths(1);
            return prevMonth.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @Component
    public static class PreviousMonthEndFunction implements WorkflowFunction {
        @Override
        public String name() {
            return "DateUtils.previousMonthEnd";
        }

        @Override
        public Object execute(List<Object> arguments, ExecutionContext context) {
            ZoneId zone = getZone(context);
            LocalDate prevMonth = LocalDate.now(zone).minusMonths(1);
            return prevMonth.withDayOfMonth(prevMonth.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private static ZoneId getZone(ExecutionContext context) {
        if (context != null && context.timezone() != null) {
            try {
                return ZoneId.of(context.timezone());
            } catch (Exception ignored) {}
        }
        return ZoneId.of("UTC");
    }
}
