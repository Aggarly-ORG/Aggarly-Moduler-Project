package com.luna.aggarly.scheduler.dto.plan;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = OnceTriggerConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OnceTriggerConfig.class),
        @JsonSubTypes.Type(value = DailyTriggerConfig.class),
        @JsonSubTypes.Type(value = WeeklyTriggerConfig.class),
        @JsonSubTypes.Type(value = MonthlyTriggerConfig.class),
        @JsonSubTypes.Type(value = IntervalTriggerConfig.class),
        @JsonSubTypes.Type(value = EventTriggerConfig.class),
        @JsonSubTypes.Type(value = EventOffsetTriggerConfig.class)
})
public sealed interface TriggerConfig
        permits OnceTriggerConfig,
                DailyTriggerConfig,
                WeeklyTriggerConfig,
                MonthlyTriggerConfig,
                IntervalTriggerConfig,
                EventTriggerConfig,
                EventOffsetTriggerConfig {
}
