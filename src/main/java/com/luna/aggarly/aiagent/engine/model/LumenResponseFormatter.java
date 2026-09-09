package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class LumenResponseFormatter {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Formats any raw LLM response into a guaranteed valid Lumen JSON presentation contract.
     */
    public static String formatResponse(String rawLlmOutput, List<LumenResponseBlock> backendBlocks) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            LumenAgentResponse fallback = LumenAgentResponse.of(backendBlocks != null ? backendBlocks : List.of());
            return serialize(fallback);
        }

        String cleaned = cleanJsonString(rawLlmOutput);

        try {
            JsonNode node = tryParseJson(cleaned);
            if (node != null && node.has("blocks") && node.get("blocks").isArray()) {
                LumenAgentResponse parsed = objectMapper.treeToValue(node, LumenAgentResponse.class);
                if (parsed.getVersion() == null) {
                    parsed.setVersion("1");
                }
                normalizeBlockAliases(parsed.getBlocks());
                
                // If backend blocks are provided, synchronize and enrich matching data blocks with authoritative backend data
                if (backendBlocks != null && !backendBlocks.isEmpty()) {
                    List<LumenResponseBlock> mergedBlocks = new ArrayList<>(parsed.getBlocks());
                    for (LumenResponseBlock bb : backendBlocks) {
                        int targetIdx = findMergeTargetIndex(mergedBlocks, bb);
                        if (targetIdx >= 0) {
                            applyBackendData(mergedBlocks.get(targetIdx), bb);
                        } else if ("execution_plan".equals(bb.getType())) {
                            mergedBlocks.add(0, bb);
                        } else {
                            mergedBlocks.add(bb);
                        }
                    }
                    parsed.setBlocks(mergedBlocks);
                }

                // Normalize price_breakdown and other block fields
                if (parsed.getBlocks() != null) {
                    for (LumenResponseBlock b : parsed.getBlocks()) {
                        if ("price_breakdown".equals(b.getType()) && b.getData() != null) {
                            Map<String, Object> data = new HashMap<>(b.getData());
                            if (!data.containsKey("currency")) {
                                data.put("currency", "€");
                            }
                            if (!data.containsKey("title")) {
                                data.put("title", "Price Breakdown & Fees");
                            }
                            Object tot = data.get("total");
                            if (tot == null) {
                                tot = data.get("totalPrice");
                                if (tot != null) {
                                    data.put("total", tot);
                                }
                            }
                            if (!data.containsKey("items") || data.get("items") == null) {
                                List<Map<String, Object>> fallbackItems = new ArrayList<>();
                                Object base = data.get("basePrice");
                                if (base != null) {
                                    fallbackItems.add(Map.of("label", "Base Nightly Rate", "amount", base));
                                }
                                if (tot != null && base != null) {
                                    try {
                                        double dTot = Double.parseDouble(tot.toString());
                                        double dBase = Double.parseDouble(base.toString());
                                        if (dTot > dBase) {
                                            fallbackItems.add(Map.of("label", "Taxes, Cleaning & Hospitality Fees", "amount", Math.round((dTot - dBase) * 100.0) / 100.0));
                                        }
                                    } catch (Exception ignored) {}
                                }
                                data.put("items", fallbackItems);
                            }
                            b.setData(data);
                        } else if ("actions".equals(b.getType()) && b.getItems() != null) {
                            List<Map<String, Object>> normalizedItems = new ArrayList<>();
                            for (Map<String, Object> rawItem : b.getItems()) {
                                Map<String, Object> item = new HashMap<>(rawItem);
                                String actionName = (String) item.get("action");
                                if (actionName == null) {
                                    actionName = (String) item.get("name");
                                }

                                if (item.containsKey("params") && !item.containsKey("parameters")) {
                                    item.put("parameters", item.get("params"));
                                }
                                if (item.containsKey("arguments") && !item.containsKey("parameters")) {
                                    item.put("parameters", item.get("arguments"));
                                }
                                if (item.containsKey("inputFields") && !item.containsKey("inputs")) {
                                    item.put("inputs", item.get("inputFields"));
                                }
                                if (item.containsKey("input") && !item.containsKey("inputs")) {
                                    Object inp = item.get("input");
                                    item.put("inputs", inp instanceof List ? inp : List.of(inp));
                                }

                                if ("chat.message_host".equals(actionName) || "message_host".equals(actionName) || "message".equals(actionName)) {
                                    item.put("requiresInput", true);
                                    if (!item.containsKey("inputs") || item.get("inputs") == null) {
                                        item.put("inputs", List.of(Map.of(
                                                "name", "message",
                                                "type", "textarea",
                                                "label", "Message to Host",
                                                "placeholder", "Type your message to the host here...",
                                                "required", true
                                        )));
                                    }
                                } else if ("notification.priceTracking".equals(actionName) || "notification.createAlert".equals(actionName) || "price.alert".equals(actionName)) {
                                    item.put("requiresInput", true);
                                    if (!item.containsKey("inputs") || item.get("inputs") == null) {
                                        item.put("inputs", List.of(Map.of(
                                                "name", "targetPrice",
                                                "type", "number",
                                                "label", "Target Nightly Price (€)",
                                                "placeholder", "e.g. 350",
                                                "required", true
                                        )));
                                    }
                                } else if ("review.create".equals(actionName) || "submit_review".equals(actionName)) {
                                    item.put("requiresInput", true);
                                    if (!item.containsKey("inputs") || item.get("inputs") == null) {
                                        item.put("inputs", List.of(
                                                Map.of("name", "rating", "type", "number", "label", "Rating (1-5)", "placeholder", "5", "required", true),
                                                Map.of("name", "comment", "type", "textarea", "label", "Review Comment", "placeholder", "Write your review...", "required", true)
                                        ));
                                    }
                                }

                                if (item.containsKey("inputs") && item.get("inputs") instanceof List && !((List<?>) item.get("inputs")).isEmpty()) {
                                    item.put("requiresInput", true);
                                }

                                Object itemId = item.get("id");
                                if (itemId == null || String.valueOf(itemId).isBlank()) {
                                    String base = actionName != null ? actionName : "action";
                                    item.put("id", base.replace('.', '-') + "-" + normalizedItems.size());
                                }

                                normalizedItems.add(item);
                            }
                            b.setItems(normalizedItems);
                        }
                    }

                    // Clean up empty or hallucinated blocks with no data
                    parsed.getBlocks().removeIf(b -> {
                        if ("booking_status".equals(b.getType()) || "booking".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("bookingId") && !d.containsKey("propertyTitle") && !d.containsKey("id") && !d.containsKey("accessCode") && !d.containsKey("checkInDate")));
                        }
                        if ("property".equals(b.getType()) || "property_card".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("id") && !d.containsKey("title") && !d.containsKey("propertyId")));
                        }
                        if ("price_breakdown".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("total") && !d.containsKey("totalPrice") && !d.containsKey("basePrice")));
                        }
                        if ("photo_tour_preview".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("propertyId") && !d.containsKey("scenes")));
                        }
                        if ("html".equals(b.getType()) || "html_snippet".equals(b.getType()) || "html_block".equals(b.getType()) || "iframe".equals(b.getType()) || "embed".equals(b.getType()) || "webview".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return (d == null || (!d.containsKey("src") && !d.containsKey("html") && !d.containsKey("url"))) && (b.getContent() == null || b.getContent().isBlank());
                        }
                        return false;
                    });
                }

                sanitize(parsed.getBlocks());
                return serialize(parsed);
            }
        } catch (Exception e) {
            log.debug("LLM output is not direct JSON. Wrapping as text block.", e);
        }

        // If the LLM returned natural language text, wrap it into blocks
        List<LumenResponseBlock> blocks = new ArrayList<>();
        if (backendBlocks != null) {
            for (LumenResponseBlock bb : backendBlocks) {
                if ("execution_plan".equals(bb.getType())) {
                    blocks.add(bb);
                }
            }
        }

        blocks.add(LumenResponseBlock.text(cleaned));

        if (backendBlocks != null) {
            for (LumenResponseBlock bb : backendBlocks) {
                if (!"execution_plan".equals(bb.getType())) {
                    blocks.add(bb);
                }
            }
        }

        sanitize(blocks);

        LumenAgentResponse response = LumenAgentResponse.builder()
                .version("1")
                .blocks(blocks)
                .build();

        return serialize(response);
    }

    @SuppressWarnings("unchecked")
    public static List<LumenResponseBlock> extractBlocksFromMetadata(Map<String, Object> metadataCollector) {
        List<LumenResponseBlock> blocks = new ArrayList<>();
        if (metadataCollector == null || metadataCollector.isEmpty()) {
            return blocks;
        }

        String cardType = (String) metadataCollector.get("cardType");
        if ("PROPERTY_SEARCH".equals(cardType) && metadataCollector.containsKey("properties")) {
            blocks.add(LumenResponseBlock.propertyList((List<?>) metadataCollector.get("properties")));
        } else if ("PROPERTY_DETAILS".equals(cardType) && metadataCollector.containsKey("property")) {
            Object prop = metadataCollector.get("property");
            Map<String, Object> propMap = prop instanceof Map ? (Map<String, Object>) prop : objectMapper.convertValue(prop, Map.class);
            blocks.add(LumenResponseBlock.property(propMap));
        } else if ("PHOTO_TOUR".equals(cardType) && metadataCollector.containsKey("photoTour")) {
            Object tour = metadataCollector.get("photoTour");
            Map<String, Object> tourMap = tour instanceof Map ? (Map<String, Object>) tour : objectMapper.convertValue(tour, Map.class);
            blocks.add(LumenResponseBlock.photoTourPreview(tourMap));
        } else if ("AVAILABILITY_CALENDAR".equals(cardType) && metadataCollector.containsKey("calendarData")) {
            Object cal = metadataCollector.get("calendarData");
            Map<String, Object> calMap = cal instanceof Map ? (Map<String, Object>) cal : objectMapper.convertValue(cal, Map.class);
            blocks.add(LumenResponseBlock.availability(calMap));
        } else if ("BOOKING_TIMELINE".equals(cardType) && metadataCollector.containsKey("timelineData")) {
            Object timeline = metadataCollector.get("timelineData");
            Map<String, Object> timeMap = timeline instanceof Map ? (Map<String, Object>) timeline : objectMapper.convertValue(timeline, Map.class);
            blocks.add(LumenResponseBlock.bookingStatus(timeMap));
        } else if ("PAYMENT_PROMPT".equals(cardType) && metadataCollector.containsKey("paymentData")) {
            Object pay = metadataCollector.get("paymentData");
            Map<String, Object> payMap = pay instanceof Map ? (Map<String, Object>) pay : objectMapper.convertValue(pay, Map.class);
            blocks.add(LumenResponseBlock.paymentStatus(payMap));
            blocks.add(LumenResponseBlock.booking(payMap));

            Object bookingId = payMap.get("bookingId") != null ? payMap.get("bookingId") : payMap.get("id");
            Object total = payMap.get("totalAmount") != null ? payMap.get("totalAmount") : payMap.get("totalPrice");
            Object currency = payMap.get("currency") != null ? payMap.get("currency") : "€";
            Object clientSecret = payMap.get("clientSecret");

            if (bookingId != null) {
                Map<String, Object> payAction = new LinkedHashMap<>();
                payAction.put("id", "pay-" + bookingId);
                payAction.put("label", "Proceed to Payment (" + currency + (total != null ? total : "") + ")");
                payAction.put("variant", "primary");
                payAction.put("action", "payment.confirm");
                payAction.put("bookingId", bookingId.toString());
                if (clientSecret != null) {
                    payAction.put("clientSecret", clientSecret.toString());
                }
                payAction.put("paymentUrl", "/api/v1/payments/" + bookingId + "/confirm");

                Map<String, Object> cancelAction = new LinkedHashMap<>();
                cancelAction.put("id", "cancel-" + bookingId);
                cancelAction.put("label", "Cancel Reservation");
                cancelAction.put("variant", "secondary");
                cancelAction.put("action", "booking.cancel");
                cancelAction.put("bookingId", bookingId.toString());

                blocks.add(LumenResponseBlock.actions(List.of(payAction, cancelAction)));
            }
        } else if ("CANCELLATION_POLICY".equals(cardType) && metadataCollector.containsKey("policyData")) {
            Object policy = metadataCollector.get("policyData");
            Map<String, Object> polMap = policy instanceof Map ? (Map<String, Object>) policy : objectMapper.convertValue(policy, Map.class);
            blocks.add(LumenResponseBlock.builder().type("warning").data(polMap).build());
        } else if ("VISION_SEARCH".equals(cardType)) {
            if (metadataCollector.containsKey("visionResults")) {
                Map<String, Object> visionData = new LinkedHashMap<>();
                visionData.put("results", metadataCollector.get("visionResults"));
                if (metadataCollector.containsKey("textQuery")) {
                    visionData.put("textQuery", metadataCollector.get("textQuery"));
                }
                if (metadataCollector.containsKey("queryImagePreviewUrl")) {
                    visionData.put("queryImagePreviewUrl", metadataCollector.get("queryImagePreviewUrl"));
                }
                blocks.add(LumenResponseBlock.builder().type("vision_results").data(visionData).build());
            }
            if (metadataCollector.containsKey("properties")) {
                blocks.add(LumenResponseBlock.propertyList((List<?>) metadataCollector.get("properties")));
            }
        } else if ("PROPERTY_COMPARE".equals(cardType) && metadataCollector.containsKey("compareData")) {
            Object comp = metadataCollector.get("compareData");
            Map<String, Object> compMap = comp instanceof Map ? (Map<String, Object>) comp : objectMapper.convertValue(comp, Map.class);
            blocks.add(LumenResponseBlock.builder().type("compare").data(compMap).build());
        }

        if (!"PHOTO_TOUR".equals(cardType) && metadataCollector.containsKey("photoTour")) {
            Object tour = metadataCollector.get("photoTour");
            Map<String, Object> tourMap = tour instanceof Map ? (Map<String, Object>) tour : objectMapper.convertValue(tour, Map.class);
            blocks.add(LumenResponseBlock.photoTourPreview(tourMap));
        }

        if (metadataCollector.containsKey("priceBreakdown")) {
            Object price = metadataCollector.get("priceBreakdown");
            Map<String, Object> priceMap = price instanceof Map ? (Map<String, Object>) price : objectMapper.convertValue(price, Map.class);
            blocks.add(LumenResponseBlock.priceBreakdown(priceMap));
        }

        if (metadataCollector.containsKey("lastScheduleAction")) {
            Object act = metadataCollector.get("lastScheduleAction");
            if (act instanceof List<?> list) {
                blocks.add(LumenResponseBlock.scheduledTaskList(list));
            } else if (act != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> taskMap = act instanceof Map ? (Map<String, Object>) act : objectMapper.convertValue(act, Map.class);
                blocks.add(LumenResponseBlock.scheduledTask(taskMap));
            }
        }

        if (metadataCollector.containsKey("actionChips") && metadataCollector.get("actionChips") instanceof List<?> chips) {
            blocks.add(LumenResponseBlock.actionChips((List<Map<String, Object>>) chips));
        }

        if (metadataCollector.containsKey("quickReplies") && metadataCollector.get("quickReplies") instanceof List<?> replies) {
            blocks.add(LumenResponseBlock.quickReplies((List<String>) replies));
        }

        return blocks;
    }

    /**
     * Parses JSON directly, retrying once on prose-wrapped or fenced JSON by slicing
     * the outermost brace pair (common LLM behavior: "Here is the response: {...}").
     */
    private static JsonNode tryParseJson(String cleaned) {
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            // fall through to brace-slice retry
        }
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first >= 0 && last > first) {
            try {
                return objectMapper.readTree(cleaned.substring(first, last + 1));
            } catch (Exception ignored) {
                // not recoverable JSON
            }
        }
        return null;
    }

    /** Canonicalizes LLM-invented block type spellings so downstream merge/dedup/rendering see stable types. */
    private static final Map<String, String> BLOCK_TYPE_ALIASES = Map.ofEntries(
            Map.entry("iframe", "html"),
            Map.entry("webview", "html"),
            Map.entry("embed", "html"),
            Map.entry("html_block", "html"),
            Map.entry("html_snippet", "html_snippet"),
            Map.entry("schedule", "scheduled_task"),
            Map.entry("tasks", "scheduled_task_list"),
            Map.entry("scheduled_tasks", "scheduled_task_list"),
            Map.entry("action", "actions"),
            Map.entry("suggested_actions", "actions"),
            Map.entry("chips", "action_chips"),
            Map.entry("quick_reply", "quick_replies"),
            Map.entry("photo_tour", "photo_tour_preview"),
            Map.entry("tour", "photo_tour_preview")
    );

    private static void normalizeBlockAliases(List<LumenResponseBlock> blocks) {
        if (blocks == null) return;
        for (LumenResponseBlock b : blocks) {
            if (b.getType() == null) continue;
            String t = b.getType().trim().toLowerCase(Locale.ROOT);
            b.setType(BLOCK_TYPE_ALIASES.getOrDefault(t, t));
        }
    }

    /** Business identity keys ordered from most specific to most generic. */
    private static final List<String> IDENTITY_KEYS = List.of(
            "confirmationToken", "bookingId", "paymentId", "propertyId", "taskId", "scheduleId", "id");

    private static Object businessIdentity(LumenResponseBlock block) {
        if (block == null || block.getData() == null) return null;
        for (String key : IDENTITY_KEYS) {
            Object val = block.getData().get(key);
            if (val != null) return String.valueOf(val);
        }
        return null;
    }

    /**
     * Finds the LLM-emitted block that a given authoritative backend block should enrich.
     * Matching strategy:
     * 1. Same type AND same business identity (e.g. same bookingId/property id/token).
     * 2. Same type where the LLM block is a skeleton (no identity).
     * 3. Single same-type counterpart (legacy enrichment for single-card responses).
     * Returns -1 when the backend block describes a distinct entity and must be appended.
     */
    private static int findMergeTargetIndex(List<LumenResponseBlock> blocks, LumenResponseBlock backend) {
        String type = backend.getType();
        if (type == null) return -1;

        Object backendId = businessIdentity(backend);
        if (backendId != null) {
            for (int i = 0; i < blocks.size(); i++) {
                LumenResponseBlock candidate = blocks.get(i);
                if (type.equals(candidate.getType()) && backendId.equals(businessIdentity(candidate))) {
                    return i;
                }
            }
        }

        int sameTypeCount = 0;
        int firstAny = -1;
        int firstSkeleton = -1;
        for (int i = 0; i < blocks.size(); i++) {
            LumenResponseBlock candidate = blocks.get(i);
            if (!type.equals(candidate.getType())) continue;
            sameTypeCount++;
            if (firstAny < 0) firstAny = i;
            if (firstSkeleton < 0 && businessIdentity(candidate) == null) firstSkeleton = i;
        }
        if (sameTypeCount == 0) return -1;
        if (backendId == null) return firstSkeleton >= 0 ? firstSkeleton : firstAny;
        if (sameTypeCount == 1) return firstAny;
        // Multiple distinct-identity cards of this type already rendered; appending instead.
        return -1;
    }

    /** Authoritative backend data overrides/enriches the LLM-generated block. */
    private static void applyBackendData(LumenResponseBlock target, LumenResponseBlock backend) {
        if (backend.getData() != null) {
            Map<String, Object> merged = new HashMap<>();
            if (target.getData() != null) {
                merged.putAll(target.getData());
            }
            merged.putAll(backend.getData());

            if (merged.containsKey("status") && !merged.containsKey("currentStep")) {
                String st = String.valueOf(merged.get("status")).toUpperCase();
                if (st.equals("CREATED") || st.equals("PENDING")) {
                    merged.put("currentStep", "HELD");
                } else if (st.equals("PAID") || st.equals("AUTHORIZED")) {
                    merged.put("currentStep", "AUTHORIZED");
                } else if (st.equals("CONFIRMED") || st.equals("ACTIVE")) {
                    merged.put("currentStep", "CHECKIN_READY");
                } else if (st.equals("COMPLETED")) {
                    merged.put("currentStep", "COMPLETED");
                }
            }
            target.setData(merged);
        }
        if (backend.getItems() != null && !backend.getItems().isEmpty()) {
            target.setItems(backend.getItems());
        }
    }

    /**
     * Final safety net applied to every formatted response: collapses duplicated,
     * redundant or conflicting blocks regardless of how many layers added them
     * (agent, planning engine, conversation manager).
     */
    private static void sanitize(List<LumenResponseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return;

        boolean[] remove = new boolean[blocks.size()];
        Set<String> seenTexts = new HashSet<>();
        Set<String> seenExactContent = new HashSet<>();
        Set<String> seenConfirmationTokens = new HashSet<>();
        Set<String> seenActionSignatures = new HashSet<>();

        int bestPlanIdx = -1;
        int bestPlanScore = -1;

        for (int i = 0; i < blocks.size(); i++) {
            LumenResponseBlock b = blocks.get(i);
            String type = b.getType() == null ? "" : b.getType();
            switch (type) {
                case "text" -> {
                    String content = b.getContent();
                    if (content == null || content.isBlank() || !seenTexts.add(content.trim())) {
                        remove[i] = true;
                    }
                }
                case "execution_plan" -> {
                    remove[i] = true;
                }
                case "confirmation" -> {
                    Object token = b.getData() != null ? b.getData().get("confirmationToken") : null;
                    String key = token != null ? token.toString() : "__anonymous__";
                    if (!seenConfirmationTokens.add(key)) {
                        remove[i] = true;
                    }
                }
                case "actions", "action_chips", "quick_replies" -> {
                    if (!seenActionSignatures.add(actionSignature(b))) {
                        remove[i] = true;
                    }
                }
                default -> {
                    String signature = type + "|" + Objects.hashCode(b.getData()) + "|" + Objects.hashCode(b.getItems())
                            + "|" + Objects.hashCode(b.getContent());
                    if (!seenExactContent.add(signature)) {
                        remove[i] = true;
                    }
                }
            }
        }

        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (remove[i]) {
                blocks.remove(i);
            }
        }
    }

    /** Richer execution plans (more steps/events) win; the live trace typically beats agent-local summaries. */
    private static int planRichnessScore(LumenResponseBlock plan) {
        if (plan.getData() == null) return 0;
        int score = 0;
        Object steps = plan.getData().get("steps");
        if (steps instanceof List<?> l) score += l.size() * 10;
        Object events = plan.getData().get("events");
        if (events instanceof List<?> l) score += l.size();
        Object totalSteps = plan.getData().get("totalSteps");
        if (totalSteps instanceof Number n) score += n.intValue();
        return score;
    }

    /** Two actions blocks are duplicates when they expose the same set of action ids. */
    private static String actionSignature(LumenResponseBlock actions) {
        if (actions.getItems() == null || actions.getItems().isEmpty()) return "empty";
        return actions.getItems().stream()
                .map(item -> String.valueOf(item.get("id") != null ? item.get("id") : (item.get("action") != null ? item.get("action") : item.get("label"))))
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private static String cleanJsonString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private static String serialize(LumenAgentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to serialize LumenAgentResponse", e);
            return "{\"version\":\"1\",\"blocks\":[{\"type\":\"text\",\"content\":\"I processed your request.\"}]}";
        }
    }
}
