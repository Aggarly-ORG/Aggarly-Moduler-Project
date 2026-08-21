package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has("blocks") && node.get("blocks").isArray()) {
                LumenAgentResponse parsed = objectMapper.treeToValue(node, LumenAgentResponse.class);
                if (parsed.getVersion() == null) {
                    parsed.setVersion("1");
                }
                
                // If backend blocks are provided, synchronize and enrich matching data blocks with authoritative backend data
                if (backendBlocks != null && !backendBlocks.isEmpty()) {
                    List<LumenResponseBlock> mergedBlocks = new ArrayList<>(parsed.getBlocks());
                    for (LumenResponseBlock bb : backendBlocks) {
                        boolean matched = false;
                        for (int i = 0; i < mergedBlocks.size(); i++) {
                            LumenResponseBlock mb = mergedBlocks.get(i);
                            if (mb.getType() != null && mb.getType().equals(bb.getType())) {
                                // Authoritative backend data overrides or enriches LLM generated data (e.g. real images, real prices, real property lists)
                                if (bb.getData() != null) {
                                    Map<String, Object> merged = new HashMap<>();
                                    if (mb.getData() != null) {
                                        merged.putAll(mb.getData());
                                    }
                                    merged.putAll(bb.getData());
                                    
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
                                    mb.setData(merged);
                                }
                                if (bb.getItems() != null && !bb.getItems().isEmpty()) {
                                    mb.setItems(bb.getItems());
                                }
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
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
                                String actionName = (String) item.getOrDefault("action", item.get("id"));

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
                                } else if ("notification.priceTracking".equals(actionName) || "price.alert".equals(actionName)) {
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
                        if ("property".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("id") && !d.containsKey("title")));
                        }
                        if ("price_breakdown".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return d == null || (d.isEmpty() || (!d.containsKey("total") && !d.containsKey("totalPrice") && !d.containsKey("basePrice")));
                        }
                        if ("html".equals(b.getType()) || "html_block".equals(b.getType()) || "iframe".equals(b.getType()) || "embed".equals(b.getType()) || "webview".equals(b.getType())) {
                            Map<String, Object> d = b.getData();
                            return (d == null || (!d.containsKey("src") && !d.containsKey("html") && !d.containsKey("url"))) && (b.getContent() == null || b.getContent().isBlank());
                        }
                        return false;
                    });
                }

                return serialize(parsed);
            }
        } catch (Exception e) {
            log.debug("LLM output is not direct JSON. Wrapping as text block.", e);
        }

        // If the LLM returned natural language text, wrap it into blocks
        List<LumenResponseBlock> blocks = new ArrayList<>();
        blocks.add(LumenResponseBlock.text(cleaned));

        if (backendBlocks != null) {
            blocks.addAll(backendBlocks);
        }

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
        } else if ("CANCELLATION_POLICY".equals(cardType) && metadataCollector.containsKey("policyData")) {
            Object policy = metadataCollector.get("policyData");
            Map<String, Object> polMap = policy instanceof Map ? (Map<String, Object>) policy : objectMapper.convertValue(policy, Map.class);
            blocks.add(LumenResponseBlock.builder().type("warning").data(polMap).build());
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

        return blocks;
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
