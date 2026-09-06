package com.luna.aggarly.aiagent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.aiagent.tool.schedule.ScheduleCreateTool;
import com.luna.aggarly.scheduler.dto.plan.TriggerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonSchemaServiceTest {

    private final JsonSchemaService schemaService = new JsonSchemaService();

    @Test
    public void testGenerateCreateScheduleParamsSchema() throws Exception {
        JsonNode schema = schemaService.generate(ScheduleCreateTool.Params.class);
        assertNotNull(schema);
        System.out.println("Generated ScheduleCreateTool.Params Schema:\n" +
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(schema));

        assertTrue(schema.has("$schema"));
        assertTrue(schema.has("$defs"));
        assertTrue(schema.has("$ref"));

        JsonNode defs = schema.get("$defs");
        assertNotNull(defs);

        // Check root record definition
        String rootDefName = ScheduleCreateTool.Params.class.getName().replace('.', '_').replace('$', '_');
        assertTrue(defs.has(rootDefName), "Should contain root definition in $defs: " + rootDefName);

        JsonNode rootDef = defs.get(rootDefName);
        assertEquals("object", rootDef.get("type").asText());
        assertTrue(rootDef.has("properties"));

        JsonNode properties = rootDef.get("properties");
        assertTrue(properties.has("name"));
        assertTrue(properties.has("triggerType"));
        assertTrue(properties.has("triggerConfig"));
        assertTrue(properties.has("plan"));

        // Check that triggerConfig has oneOf with permitted subtypes
        JsonNode triggerConfigNode = properties.get("triggerConfig");
        assertTrue(triggerConfigNode.has("oneOf"), "triggerConfig should have oneOf for sealed TriggerConfig");
        assertEquals(7, triggerConfigNode.get("oneOf").size(), "Should have 7 permitted subclasses for TriggerConfig");
    }
}
