package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.luna.aggarly.vision.dto.PhotoTourWalkthroughResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public static LumenResponseBlock propertyCard(Map<String, Object> propertyCardData) {
        return LumenResponseBlock.builder()
                .type("property_card")
                .data(propertyCardData)
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

    public static LumenResponseBlock photoTourPreview(PhotoTourWalkthroughResponse tour) {
        Map<String, Object> tourMap = new LinkedHashMap<>();
        if (tour != null) {
            tourMap.put("propertyId", tour.propertyId());
            tourMap.put("title", tour.propertyTitle());
            tourMap.put("totalScenes", tour.totalScenes());
            tourMap.put("scenes", tour.scenes());
            tourMap.put("highlightedAmenities", tour.highlightedAmenities());
            tourMap.put("visualSummary", tour.visualSummary());
        }
        return LumenResponseBlock.builder()
                .type("photo_tour_preview")
                .data(tourMap)
                .build();
    }

    public static LumenResponseBlock photoTourPreview(Map<String, Object> tourData) {
        return LumenResponseBlock.builder()
                .type("photo_tour_preview")
                .data(tourData)
                .build();
    }

    public static LumenResponseBlock actions(List<Map<String, Object>> actionItems) {
        return LumenResponseBlock.builder()
                .type("actions")
                .items(actionItems)
                .build();
    }

    public static LumenResponseBlock actionChips(List<Map<String, Object>> chips) {
        return LumenResponseBlock.builder()
                .type("action_chips")
                .items(chips)
                .build();
    }

    public static LumenResponseBlock quickReplies(List<String> replies) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (replies != null) {
            for (String reply : replies) {
                items.add(Map.of(
                        "label", reply,
                        "action", "quick_reply",
                        "text", reply
                ));
            }
        }
        return LumenResponseBlock.builder()
                .type("quick_replies")
                .items(items)
                .build();
    }

    public static Map<String, Object> datePickerChip(UUID propertyId, String label) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("type", "DATE_PICKER");
        chip.put("label", label != null ? label : "Select Dates");
        chip.put("action", "select_dates");
        if (propertyId != null) chip.put("propertyId", propertyId.toString());
        return chip;
    }

    public static Map<String, Object> visualSearchChip(String suggestedQuery) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("type", "VISUAL_SEARCH");
        chip.put("label", "Search Similar Photos");
        chip.put("action", "vision_search");
        if (suggestedQuery != null) chip.put("suggestedQuery", suggestedQuery);
        return chip;
    }

    public static Map<String, Object> scheduleTourChip(UUID propertyId, UUID hostId) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("type", "SCHEDULE");
        chip.put("label", "Schedule Visit / Tour");
        chip.put("action", "schedule_tour");
        if (propertyId != null) chip.put("propertyId", propertyId.toString());
        if (hostId != null) chip.put("hostId", hostId.toString());
        return chip;
    }

    public static Map<String, Object> quickReplyChip(String label, String message) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("type", "QUICK_REPLY");
        chip.put("label", label);
        chip.put("action", "quick_reply");
        chip.put("message", message != null ? message : label);
        return chip;
    }

    public static LumenResponseBlock confirmation(Map<String, Object> confirmationData) {
        return LumenResponseBlock.builder()
                .type("confirmation")
                .data(confirmationData)
                .build();
    }

    public static LumenResponseBlock scheduleConfirmation(UUID taskId, String taskName, String triggerDescription, String nextRunFormatted) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (taskId != null) data.put("taskId", taskId.toString());
        if (taskName != null) data.put("taskName", taskName);
        if (triggerDescription != null) data.put("trigger", triggerDescription);
        if (nextRunFormatted != null) data.put("nextRun", nextRunFormatted);
        return LumenResponseBlock.builder()
                .type("schedule_confirmation")
                .data(data)
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

    public static LumenResponseBlock executionPlan(Map<String, Object> planData) {
        return LumenResponseBlock.builder()
                .type("execution_plan")
                .data(planData)
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

    public static LumenResponseBlock htmlSnippet(String title, String sanitizedHtml) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (title != null) data.put("title", title);
        if (sanitizedHtml != null) data.put("html", sanitizedHtml);
        return LumenResponseBlock.builder()
                .type("html_snippet")
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
