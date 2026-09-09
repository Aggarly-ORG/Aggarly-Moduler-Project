package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenResponseFormatterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Confirmation + actions added by agent, planner and manager collapse to one card each")
    void duplicateConfirmationAndActionsAcrossLayersCollapse() throws Exception {
        String raw = """
                {"version":"1","blocks":[
                  {"type":"text","content":"Please confirm your booking."},
                  {"type":"confirmation","data":{"title":"Booking Confirmation Required","confirmationToken":"tok-1"}},
                  {"type":"actions","items":[
                    {"id":"confirm-tok-1","action":"ai.confirm","confirmationToken":"tok-1"},
                    {"id":"cancel-tok-1","action":"ai.cancel","confirmationToken":"tok-1"}
                  ]}
                ]}
                """;

        List<LumenResponseBlock> backend = List.of(
                LumenResponseBlock.confirmation(Map.of("title", "Action Confirmation Required", "confirmationToken", "tok-1")),
                LumenResponseBlock.actions(List.of(
                        Map.of("id", "confirm-tok-1", "label", "Confirm & Proceed", "variant", "primary", "action", "ai.confirm", "confirmationToken", "tok-1"),
                        Map.of("id", "cancel-tok-1", "label", "Cancel", "variant", "secondary", "action", "ai.cancel", "confirmationToken", "tok-1"))));

        String out = LumenResponseFormatter.formatResponse(raw, backend);
        JsonNode blocks = mapper.readTree(out).get("blocks");

        assertEquals(1, countType(blocks, "confirmation"));
        assertEquals(1, countType(blocks, "actions"));
        assertEquals(1, countType(blocks, "text"));
    }


    @Test
    @DisplayName("Backend property cards enrich their matching LLM cards by id without duplicating")
    void propertyCardsEnrichByIdWithoutDuplicating() throws Exception {
        String raw = """
                {"version":"1","blocks":[
                  {"type":"text","content":"Top picks for your stay:"},
                  {"type":"property","data":{"id":"p1","title":"Sea Villa"}},
                  {"type":"property","data":{"id":"p2","title":"City Loft"}}
                ]}
                """;

        List<LumenResponseBlock> backend = List.of(
                LumenResponseBlock.property(Map.of("id", "p1", "title", "Sea Villa", "imageUrl", "http://img/p1.jpg")),
                LumenResponseBlock.property(Map.of("id", "p2", "title", "City Loft", "imageUrl", "http://img/p2.jpg")));

        String out = LumenResponseFormatter.formatResponse(raw, backend);
        JsonNode blocks = mapper.readTree(out).get("blocks");

        assertEquals(3, blocks.size(), "text + exactly two distinct property cards");
        assertEquals(2, countType(blocks, "property"));
        assertTrue(out.contains("http://img/p1.jpg"));
        assertTrue(out.contains("http://img/p2.jpg"));
    }

    @Test
    @DisplayName("Identical content blocks emitted twice collapse to one")
    void identicalContentBlocksCollapse() throws Exception {
        String raw = """
                {"version":"1","blocks":[
                  {"type":"property","data":{"id":"p1","title":"Sea Villa"}},
                  {"type":"property","data":{"id":"p1","title":"Sea Villa"}}
                ]}
                """;

        String out = LumenResponseFormatter.formatResponse(raw, List.of());
        assertEquals(1, countType(mapper.readTree(out).get("blocks"), "property"));
    }

    @Test
    @DisplayName("LLM-invented html aliases normalize to the canonical html block type")
    void iframeAliasesNormalizeToHtml() throws Exception {
        String raw = """
                {"version":"1","blocks":[
                  {"type":"iframe","data":{"src":"https://maps.example.com/embed"}},
                  {"type":"webview","data":{"html":"<b>room tour</b>"}},
                  {"type":"html_block","data":{"url":"https://tours.example.com"}}
                ]}
                """;

        String out = LumenResponseFormatter.formatResponse(raw, List.of());
        JsonNode blocks = mapper.readTree(out).get("blocks");

        assertEquals(0, countType(blocks, "iframe") + countType(blocks, "webview") + countType(blocks, "html_block"));
        assertEquals(3, countType(blocks, "html"));
    }

    @Test
    @DisplayName("Photo tour preview and action chips formats cleanly")
    void photoTourAndActionChipsFormatCleanly() throws Exception {
        UUID propId = UUID.randomUUID();
        Map<String, Object> meta = Map.of(
                "cardType", "PHOTO_TOUR",
                "photoTour", Map.of(
                        "propertyId", propId.toString(),
                        "title", "Luxury Villa Walkthrough",
                        "totalScenes", 3,
                        "scenes", List.of(
                                Map.of("roomName", "Exterior", "sceneType", "EXTERIOR"),
                                Map.of("roomName", "Living Room", "sceneType", "LIVING_ROOM"),
                                Map.of("roomName", "Master Bedroom", "sceneType", "BEDROOM")
                        )
                ),
                "actionChips", List.of(
                        LumenResponseBlock.datePickerChip(propId, "Check Availability"),
                        LumenResponseBlock.visualSearchChip("cliffside pool view")
                ),
                "quickReplies", List.of("Show Reviews", "Book Stay")
        );

        List<LumenResponseBlock> backend = LumenResponseFormatter.extractBlocksFromMetadata(meta);
        assertEquals(3, backend.size());

        String out = LumenResponseFormatter.formatResponse("Here is your property walkthrough.", backend);
        JsonNode blocks = mapper.readTree(out).get("blocks");

        assertEquals(1, countType(blocks, "photo_tour_preview"));
        assertEquals(1, countType(blocks, "action_chips"));
        assertEquals(1, countType(blocks, "quick_replies"));
        assertEquals(1, countType(blocks, "text"));
    }

    @Test
    @DisplayName("Prose-wrapped Lumen JSON is recovered instead of being flattened to text")
    void proseWrappedJsonStillParsesIntoBlocks() throws Exception {
        String raw = "Here are your options:\n{\"blocks\":[{\"type\":\"text\",\"content\":\"Santorini picks\"}]}";

        String out = LumenResponseFormatter.formatResponse(raw, List.of());
        JsonNode root = mapper.readTree(out);

        assertEquals("1", root.path("version").asText());
        JsonNode blocks = root.get("blocks");
        assertEquals(1, blocks.size());
        assertEquals("Santorini picks", blocks.get(0).path("content").asText());
    }

    @Test
    @DisplayName("Actions items missing ids get stable generated ids")
    void actionsWithoutIdsGetGeneratedIds() throws Exception {
        String raw = """
                {"version":"1","blocks":[
                  {"type":"actions","items":[
                    {"action":"booking.cancel"},
                    {"action":"wishlist.add"}
                  ]}
                ]}
                """;

        String out = LumenResponseFormatter.formatResponse(raw, List.of());
        JsonNode items = mapper.readTree(out).get("blocks").get(0).get("items");

        assertTrue(items.get(0).hasNonNull("id"));
        assertTrue(items.get(1).hasNonNull("id"));
        assertTrue(!items.get(0).get("id").asText().equals(items.get(1).get("id").asText()));
    }

    private int countType(JsonNode blocks, String type) {
        int count = 0;
        for (JsonNode b : blocks) {
            if (type.equals(b.path("type").asText())) {
                count++;
            }
        }
        return count;
    }
}
