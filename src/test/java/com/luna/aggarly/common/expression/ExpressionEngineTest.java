package com.luna.aggarly.common.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.common.expression.library.CollectionFunctionLibrary;
import com.luna.aggarly.common.expression.library.ContextFunctionLibrary;
import com.luna.aggarly.common.expression.library.DateMathFunctionLibrary;
import com.luna.aggarly.common.expression.library.EntityFunctionLibrary;
import com.luna.aggarly.common.expression.library.JsonFunctionLibrary;
import com.luna.aggarly.common.expression.library.MathFunctionLibrary;
import com.luna.aggarly.common.expression.library.SecurityUtilityFunctionLibrary;
import com.luna.aggarly.common.expression.library.StringFunctionLibrary;
import com.luna.aggarly.common.expression.resolver.ObjNamespaceResolver;
import com.luna.aggarly.common.expression.resolver.StepNamespaceResolver;
import com.luna.aggarly.common.expression.resolver.UserNamespaceResolver;
import com.luna.aggarly.scheduler.workflow.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionEngineTest {

    private ExpressionEngine engine;
    private ExpressionValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        engine = new ExpressionEngine(null, null, null, objectMapper);

        // Register resolvers
        engine.registerNamespace(new ObjNamespaceResolver());
        engine.registerNamespace(new StepNamespaceResolver());
        engine.registerNamespace(new UserNamespaceResolver());

        // Register comprehensive libraries
        engine.registerLibrary(new DateMathFunctionLibrary());
        engine.registerLibrary(new StringFunctionLibrary());
        engine.registerLibrary(new CollectionFunctionLibrary());
        engine.registerLibrary(new MathFunctionLibrary());
        engine.registerLibrary(new SecurityUtilityFunctionLibrary());
        engine.registerLibrary(new JsonFunctionLibrary(objectMapper));
        engine.registerLibrary(new ContextFunctionLibrary(null));
        engine.registerLibrary(new EntityFunctionLibrary(null, null, null, null, null));

        validator = new ExpressionValidator(engine);
    }

    @Test
    @DisplayName("Tokenizer splits arguments respecting quotes and nested parentheses")
    void testTokenizer() {
        List<String> args = Tokenizer.splitArguments("today(), 3, 'DAYS'");
        assertEquals(3, args.size());
        assertEquals("today()", args.get(0));
        assertEquals("3", args.get(1));
        assertEquals("'DAYS'", args.get(2));

        List<String> nestedArgs = Tokenizer.splitArguments("upper(first(step.search.result)), \"Hello, world!\"");
        assertEquals(2, nestedArgs.size());
        assertEquals("upper(first(step.search.result))", nestedArgs.get(0));
        assertEquals("\"Hello, world!\"", nestedArgs.get(1));
    }

    @Test
    @DisplayName("ExpressionEngine evaluates string literals, numbers, and booleans")
    void testLiterals() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        assertEquals("Hello", engine.evaluateExpression("\"Hello\"", ctx, EvaluationMode.LENIENT));
        assertEquals(42L, engine.evaluateExpression("42", ctx, EvaluationMode.LENIENT));
        assertEquals(Boolean.TRUE, engine.evaluateExpression("true", ctx, EvaluationMode.LENIENT));
        assertNull(engine.evaluateExpression("null", ctx, EvaluationMode.LENIENT));
    }

    @Test
    @DisplayName("ExpressionEngine resolves nested objects, step outputs, and array indexing [0]")
    void testNestedObjectAndStepResolution() {
        UUID userId = UUID.randomUUID();
        ExecutionContext ctx = ExecutionContext.forTask(userId, UUID.randomUUID(), UUID.randomUUID(), "UTC");

        ctx.rootObject().put("property", Map.of(
                "title", "Luxury Sea View Penthouse",
                "pricePerNight", 350.0
        ));

        ctx.stepResults().put("search", Map.of(
                "items", List.of(
                        Map.of("id", "prop-123", "name", "First Villa"),
                        Map.of("id", "prop-456", "name", "Second Villa")
                )
        ));

        // Test obj lookup
        assertEquals("Luxury Sea View Penthouse", engine.resolve("{{obj.property.title}}", ctx));

        // Test step result bracket indexing
        assertEquals("prop-123", engine.resolve("{{step.search.items[0].id}}", ctx));
        assertEquals("Second Villa", engine.resolve("{{step.search.items.1.name}}", ctx));

        // Test user resolution
        assertEquals(userId.toString(), engine.resolve("{{user.id}}", ctx));
    }

    @Test
    @DisplayName("ExpressionEngine executes built-in date math, string, math, and collection functions")
    void testBuiltInFunctions() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        ctx.stepResults().put("tags", Map.of(
                "result", List.of("sea_view", "private_pool", "luxury")
        ));

        // Upper function
        assertEquals("ALEXANDRIA", engine.resolve("{{upper('alexandria')}}", ctx));

        // Date add function
        Object dateAdded = engine.resolve("{{date_add('2026-09-01', 5, 'DAYS')}}", ctx);
        assertEquals("2026-09-06", dateAdded);

        // First & Join functions
        assertEquals("sea_view", engine.resolve("{{first(step.tags.result)}}", ctx));
        assertEquals("sea_view, private_pool, luxury", engine.resolve("{{join(step.tags.result, ', ')}}", ctx));
        assertEquals(3, engine.resolve("{{size(step.tags.result)}}", ctx));

        // Currency and Math formatting
        assertEquals("$1,500.00", engine.resolve("{{format_currency(1500.0, 'USD')}}", ctx));
        assertEquals(50.0, engine.resolve("{{percentage(25, 50)}}", ctx));

        // Security utilities
        assertEquals("j***e@example.com", engine.resolve("{{mask_email('john.doe@example.com')}}", ctx));
        assertEquals("Fallback", engine.resolve("{{coalesce(null, '', 'Fallback')}}", ctx));
        assertEquals("VIP", engine.resolve("{{ternary(true, 'VIP', 'Regular')}}", ctx));
    }

    @Test
    @DisplayName("String functions test suite")
    void testStringFunctions() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        assertEquals("luna", engine.resolve("{{lower('LUNA')}}", ctx));
        assertEquals("clean", engine.resolve("{{trim('  clean  ')}}", ctx));
        assertEquals("HelloWorld", engine.resolve("{{concat('Hello', 'World')}}", ctx));
        assertEquals("Agg", engine.resolve("{{substring('Aggarly', 0, 3)}}", ctx));
        assertEquals("Luxury Villa", engine.resolve("{{replace('Budget Villa', 'Budget', 'Luxury')}}", ctx));
        assertEquals(true, engine.resolve("{{contains('Alexandria, Egypt', 'Egypt')}}", ctx));
        assertEquals(true, engine.resolve("{{starts_with('https://aggarly.com', 'https://')}}", ctx));
        assertEquals(true, engine.resolve("{{ends_with('photo.jpg', '.jpg')}}", ctx));
        assertEquals(5, engine.resolve("{{length('Hello')}}", ctx));
        assertEquals("Cairo", engine.resolve("{{capitalize('cairo')}}", ctx));
    }

    @Test
    @DisplayName("Math functions test suite")
    void testMathFunctions() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        assertEquals(4.57, (Double) engine.resolve("{{round(4.5678, 2)}}", ctx), 0.001);
        assertEquals(5.0, (Double) engine.resolve("{{ceil(4.1)}}", ctx), 0.001);
        assertEquals(4.0, (Double) engine.resolve("{{floor(4.9)}}", ctx), 0.001);
        assertEquals(15.0, (Double) engine.resolve("{{abs(-15.0)}}", ctx), 0.001);
        assertEquals(10.0, (Double) engine.resolve("{{min(10.0, 20.0)}}", ctx), 0.001);
        assertEquals(20.0, (Double) engine.resolve("{{max(10.0, 20.0)}}", ctx), 0.001);
        assertEquals(100.0, (Double) engine.resolve("{{clamp(150.0, 0.0, 100.0)}}", ctx), 0.001);
        assertEquals(8.0, (Double) engine.resolve("{{pow(2.0, 3.0)}}", ctx), 0.001);
        assertEquals("1,234.5", engine.resolve("{{format_number(1234.5, '#,##0.0')}}", ctx));
    }

    @Test
    @DisplayName("Date and Time functions test suite")
    void testDateFunctions() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        assertEquals("2026-08-25", engine.resolve("{{date_sub('2026-09-01', 7, 'DAYS')}}", ctx));
        assertEquals(7L, engine.resolve("{{diff_days('2026-09-01', '2026-09-08')}}", ctx));
        assertEquals(48L, engine.resolve("{{diff_hours('2026-09-01T00:00:00Z', '2026-09-03T00:00:00Z')}}", ctx));
        assertEquals(false, engine.resolve("{{is_weekend('2026-09-01')}}", ctx)); // Tuesday
        assertEquals("01/09/2026", engine.resolve("{{format_date('2026-09-01', 'dd/MM/yyyy')}}", ctx));
    }

    @Test
    @DisplayName("Security and Utility functions test suite")
    void testSecurityFunctions() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UTC");

        assertNotNull(engine.resolve("{{uuid()}}", ctx));
        assertEquals("***-***-4567", engine.resolve("{{mask_phone('+1 555-123-4567')}}", ctx));
        assertEquals("SGVsbG8=", engine.resolve("{{base64_encode('Hello')}}", ctx));
        assertEquals("Hello", engine.resolve("{{base64_decode('SGVsbG8=')}}", ctx));
        assertNotNull(engine.resolve("{{hash_sha256('password')}}", ctx));
        assertEquals(true, engine.resolve("{{is_null(null)}}", ctx));
        assertEquals(true, engine.resolve("{{is_not_null('value')}}", ctx));
    }

    @Test
    @DisplayName("Context and Variable functions test suite")
    void testContextFunctions() {
        UUID userId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        ExecutionContext ctx = ExecutionContext.forTask(userId, null, null, "America/New_York");
        ctx.variables().put("conversationId", convId.toString());
        ctx.variables().put("originChannel", "WHATSAPP");

        assertEquals(userId.toString(), engine.resolve("{{current_user_id()}}", ctx));
        assertEquals(convId.toString(), engine.resolve("{{current_conversation()}}", ctx));
        assertEquals("WHATSAPP", engine.resolve("{{origin_channel()}}", ctx));
        assertEquals("America/New_York", engine.resolve("{{timezone()}}", ctx));
    }

    @Test
    @DisplayName("ExpressionValidator checks allowlist and flags unknown namespaces and functions")
    void testExpressionValidator() {
        // Valid templates
        assertTrue(validator.validateTemplate("Hello {{user.id}}, welcome to {{upper('Aggarly')}}").isValid());
        assertTrue(validator.validateTemplate("{{date_add(today(), 3, 'DAYS')}}").isValid());
        assertTrue(validator.validateTemplate("{{format_currency(150.0, 'USD')}}").isValid());

        // Invalid function
        ExpressionValidator.ValidationResult result = validator.validateTemplate("{{unknown_func(123)}}");
        assertFalse(result.isValid());
        assertTrue(result.errors().get(0).contains("unknown_func"));
    }

    @Test
    @DisplayName("Engine resolves nested availability expression with nested curlies and date math")
    void testNestedAvailabilityExpression() {
        ExecutionContext ctx = ExecutionContext.forTask(UUID.randomUUID(), null, null, "UTC");

        // Exact user expression: {{availablity({{today()}},{{date_add({{today}},3)}})}}
        Object result = engine.resolve("{{availablity({{today()}},{{date_add({{today}},3)}})}}", ctx);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(true, map.get("available"));
        assertNotNull(map.get("checkIn"));
        assertNotNull(map.get("checkOut"));

        // Clean natural expression
        Object cleanResult = engine.resolve("{{availability(today(), date_add(today(), 3))}}", ctx);
        assertNotNull(cleanResult);
    }
}
