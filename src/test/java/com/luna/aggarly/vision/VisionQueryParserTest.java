package com.luna.aggarly.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.search.VisionQueryParser;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class VisionQueryParserTest {

    private OllamaVisionClient ollamaClient;
    private VisionQueryParser parser;

    @BeforeEach
    void setUp() {
        ollamaClient = Mockito.mock(OllamaVisionClient.class);
        parser = new VisionQueryParser(ollamaClient, new ObjectMapper());
    }

    @Test
    @DisplayName("Should extract inline city, price, guests, and scene types via LLM structured JSON")
    void testExtractCityAndConstraintsViaLlm() {
        when(ollamaClient.isAvailable()).thenReturn(true);
        String mockLlmJson = """
                {
                  "city": "Santorini",
                  "country": "Greece",
                  "maxPrice": 450.0,
                  "minGuests": 4,
                  "propertyType": "VILLA",
                  "requiredScenes": ["POOL", "VIEW"],
                  "requiredAmenities": ["private_pool", "sea_view"],
                  "cleanedVisualPrompt": "luxury cliffside villa with infinity pool and panoramic sunset sea view"
                }
                """;
        when(ollamaClient.chatWithVision(eq(null), eq(VisionQueryParser.QUERY_INTENT_EXTRACTION_PROMPT), any(), any()))
                .thenReturn(Optional.of(mockLlmJson));

        String queryText = "luxury cliffside villa in Santorini with private pool under $450 for 4 guests";
        VisionSearchQuery query = parser.parse(queryText, null, null, null, 10, null);

        assertNotNull(query.hardFilters());
        assertEquals("Santorini", query.hardFilters().city(), "City should be extracted as Santorini");
        assertEquals("Greece", query.hardFilters().country(), "Country should be extracted as Greece");
        assertEquals(450.0, query.hardFilters().maxPricePerNight(), "Max price should be 450.0");
        assertEquals(4, query.hardFilters().minGuests(), "Min guests should be 4");
        assertTrue(query.hardFilters().requiredSceneTypes().contains("POOL"), "Scene POOL should be extracted");
        assertEquals("luxury cliffside villa with infinity pool and panoramic sunset sea view", query.rawText());
    }

    @Test
    @DisplayName("Should handle LLM offline gracefully without throwing")
    void testLlmOfflineFallback() {
        when(ollamaClient.isAvailable()).thenReturn(false);

        String queryText = "cozy modern chalet in Switzerland under $700";
        VisionSearchQuery query = parser.parse(queryText, null, null, null, 10, null);

        assertNotNull(query);
        assertEquals("cozy modern chalet in Switzerland under $700", query.rawText());
    }
}
