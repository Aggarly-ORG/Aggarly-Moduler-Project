package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LumenResponseBlock {

    private String type;
    private String content;
    private Map<String, Object> data;
    private List<Map<String, Object>> items;

    public static LumenResponseBlock text(String content) {
        return LumenResponseBlock.builder()
                .type("text")
                .content(content)
                .build();
    }

    public static LumenResponseBlock property(Map<String, Object> propertyData) {
        return LumenResponseBlock.builder()
                .type("property")
                .data(propertyData)
                .build();
    }

    public static LumenResponseBlock propertyList(List<?> properties) {
        return LumenResponseBlock.builder()
                .type("property_list")
                .data(Map.of("properties", properties))
                .build();
    }

    public static LumenResponseBlock availability(Map<String, Object> availabilityData) {
        return LumenResponseBlock.builder()
                .type("availability")
                .data(availabilityData)
                .build();
    }

    public static LumenResponseBlock booking(Map<String, Object> bookingData) {
        return LumenResponseBlock.builder()
                .type("booking")
                .data(bookingData)
                .build();
    }

    public static LumenResponseBlock bookingStatus(Map<String, Object> statusData) {
        return LumenResponseBlock.builder()
                .type("booking_status")
                .data(statusData)
                .build();
    }

    public static LumenResponseBlock priceBreakdown(Map<String, Object> priceData) {
        return LumenResponseBlock.builder()
                .type("price_breakdown")
                .data(priceData)
                .build();
    }

    public static LumenResponseBlock paymentStatus(Map<String, Object> paymentData) {
        return LumenResponseBlock.builder()
                .type("payment_status")
                .data(paymentData)
                .build();
    }

    public static LumenResponseBlock actions(List<Map<String, Object>> actionItems) {
        return LumenResponseBlock.builder()
                .type("actions")
                .items(actionItems)
                .build();
    }

    public static LumenResponseBlock confirmation(Map<String, Object> confirmationData) {
        return LumenResponseBlock.builder()
                .type("confirmation")
                .data(confirmationData)
                .build();
    }

    public static LumenResponseBlock warning(String message) {
        return LumenResponseBlock.builder()
                .type("warning")
                .data(Map.of("message", message))
                .build();
    }

    public static LumenResponseBlock error(String code, String message) {
        return LumenResponseBlock.builder()
                .type("error")
                .data(Map.of("code", code, "message", message))
                .build();
    }

    public static LumenResponseBlock html(String html, String src, String title, Integer height) {
        Map<String, Object> data = new HashMap<>();
        if (html != null) data.put("html", html);
        if (src != null) data.put("src", src);
        if (title != null) data.put("title", title);
        if (height != null) data.put("height", height);
        return LumenResponseBlock.builder()
                .type("html")
                .data(data)
                .build();
    }

    public static LumenResponseBlock scheduledTask(Map<String, Object> taskData) {
        return LumenResponseBlock.builder()
                .type("scheduled_task")
                .data(taskData)
                .build();
    }

    public static LumenResponseBlock scheduledTaskList(List<?> tasks) {
        return LumenResponseBlock.builder()
                .type("scheduled_task_list")
                .data(Map.of("tasks", tasks))
                .build();
    }

    public static LumenResponseBlock iframe(String src, String title, Integer height) {
        return html(null, src, title, height);
    }
}
