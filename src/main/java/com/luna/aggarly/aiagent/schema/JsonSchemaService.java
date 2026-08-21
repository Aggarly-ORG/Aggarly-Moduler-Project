package com.luna.aggarly.aiagent.schema;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class JsonSchemaService {

    private final ObjectMapper objectMapper;

    public JsonSchemaService() {
        this(new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    public JsonSchemaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public JsonNode generate(Class<?> rootType) {
        if (rootType == null || rootType == Void.class) {
            return objectMapper.createObjectNode().put("type", "null");
        }

        SchemaContext context = new SchemaContext();
        JsonNode root = generateType(rootType, context);

        if (root instanceof ObjectNode rootObject) {
            rootObject.put("$schema", "https://json-schema.org/draft/2020-12/schema");

            if (!context.definitions.isEmpty()) {
                rootObject.set("$defs", context.definitions);
            }
        }

        return root;
    }

    // ========================================================================
    // TYPE GENERATION
    // ========================================================================

    private JsonNode generateType(Type type, SchemaContext context) {
        if (type instanceof Class<?> clazz) {
            return generateClass(clazz, context);
        }

        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();

            if (rawType instanceof Class<?> rawClass) {
                // Collection<T>
                if (Collection.class.isAssignableFrom(rawClass)) {
                    Type elementType = parameterizedType.getActualTypeArguments()[0];
                    ObjectNode array = objectMapper.createObjectNode();
                    array.put("type", "array");
                    array.set("items", generateType(elementType, context));
                    return array;
                }

                // Map<K,V>
                if (Map.class.isAssignableFrom(rawClass)) {
                    Type valueType = parameterizedType.getActualTypeArguments()[1];
                    ObjectNode object = objectMapper.createObjectNode();
                    object.put("type", "object");
                    object.set("additionalProperties", generateType(valueType, context));
                    return object;
                }

                // Optional<T>
                if (Optional.class.isAssignableFrom(rawClass)) {
                    Type actualType = parameterizedType.getActualTypeArguments()[0];
                    JsonNode inner = generateType(actualType, context);
                    return nullable(inner);
                }

                return generateClass(rawClass, context);
            }
        }

        return objectMapper.createObjectNode();
    }

    // ========================================================================
    // CLASS GENERATION
    // ========================================================================

    private JsonNode generateClass(Class<?> clazz, SchemaContext context) {
        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            return schemaString();
        }

        if (clazz == UUID.class) {
            return schemaString("uuid");
        }

        if (clazz == Instant.class || clazz == OffsetDateTime.class || clazz == ZonedDateTime.class) {
            return schemaString("date-time");
        }

        if (clazz == LocalDate.class) {
            return schemaString("date");
        }

        if (clazz == LocalTime.class || clazz == OffsetTime.class) {
            return schemaString("time");
        }

        if (clazz == Boolean.class || clazz == boolean.class) {
            return objectMapper.createObjectNode().put("type", "boolean");
        }

        if (clazz == Integer.class || clazz == int.class
                || clazz == Short.class || clazz == short.class
                || clazz == Long.class || clazz == long.class
                || clazz == Byte.class || clazz == byte.class) {
            return objectMapper.createObjectNode().put("type", "integer");
        }

        if (clazz == Double.class || clazz == double.class
                || clazz == Float.class || clazz == float.class
                || clazz == BigDecimal.class) {
            return objectMapper.createObjectNode().put("type", "number");
        }

        if (clazz.isEnum()) {
            return generateEnum(clazz);
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            ObjectNode array = objectMapper.createObjectNode();
            array.put("type", "array");
            array.set("items", objectMapper.createObjectNode());
            return array;
        }

        if (clazz.isArray()) {
            ObjectNode array = objectMapper.createObjectNode();
            array.put("type", "array");
            array.set("items", generateType(clazz.getComponentType(), context));
            return array;
        }

        if (Map.class.isAssignableFrom(clazz)) {
            ObjectNode object = objectMapper.createObjectNode();
            object.put("type", "object");
            object.set("additionalProperties", objectMapper.createObjectNode());
            return object;
        }

        if (clazz.isSealed()) {
            return generateSealedType(clazz, context);
        }

        return generateObject(clazz, context);
    }

    // ========================================================================
    // OBJECT / RECORD
    // ========================================================================

    private JsonNode generateObject(Class<?> clazz, SchemaContext context) {
        String name = definitionName(clazz);

        if (context.definitions.has(name)) {
            return reference(name);
        }

        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("type", "object");

        ObjectNode properties = definition.putObject("properties");
        ArrayNode required = definition.putArray("required");

        context.definitions.set(name, definition);

        if (clazz.isRecord()) {
            RecordComponent[] components = clazz.getRecordComponents();
            if (components != null) {
                for (RecordComponent comp : components) {
                    String compName = comp.getName();
                    ObjectNode compSchema = asObjectNode(generateType(comp.getGenericType(), context));

                    JsonPropertyDescription desc = comp.getAnnotation(JsonPropertyDescription.class);
                    if (desc == null) {
                        try {
                            Field f = clazz.getDeclaredField(compName);
                            desc = f.getAnnotation(JsonPropertyDescription.class);
                        } catch (Exception ignored) {}
                    }

                    compSchema.put("description", desc != null ? desc.value() : compName);
                    applyValidationConstraintsFromComponent(comp, clazz, compSchema);

                    properties.set(compName, compSchema);

                    if (isRequiredComponent(comp, clazz)) {
                        required.add(compName);
                    }
                }
            }
        } else {
            for (Field field : getRelevantFields(clazz)) {
                String fieldName = field.getName();
                ObjectNode fieldSchema = asObjectNode(generateType(field.getGenericType(), context));

                JsonPropertyDescription description = field.getAnnotation(JsonPropertyDescription.class);
                fieldSchema.put("description", description != null ? description.value() : fieldName);

                applyValidationConstraints(field, fieldSchema);
                properties.set(fieldName, fieldSchema);

                if (isRequired(field)) {
                    required.add(fieldName);
                }
            }
        }

        if (required.isEmpty()) {
            definition.remove("required");
        }

        return reference(name);
    }

    // ========================================================================
    // SEALED TYPES
    // ========================================================================

    private JsonNode generateSealedType(Class<?> clazz, SchemaContext context) {
        ObjectNode schema = objectMapper.createObjectNode();
        ArrayNode oneOf = schema.putArray("oneOf");

        Class<?>[] permitted = clazz.getPermittedSubclasses();
        if (permitted != null) {
            for (Class<?> subtype : permitted) {
                oneOf.add(generateType(subtype, context));
            }
        }

        return schema;
    }

    // ========================================================================
    // ENUM
    // ========================================================================

    private JsonNode generateEnum(Class<?> enumClass) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "string");
        ArrayNode values = schema.putArray("enum");

        Object[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                values.add(((Enum<?>) constant).name());
            }
        }

        return schema;
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    private void applyValidationConstraints(Field field, ObjectNode schema) {
        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            schema.put("minimum", min.value());
        }

        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            schema.put("maximum", max.value());
        }

        DecimalMin decimalMin = field.getAnnotation(DecimalMin.class);
        if (decimalMin != null) {
            try {
                schema.put("minimum", new BigDecimal(decimalMin.value()));
            } catch (NumberFormatException ignored) {}
        }

        DecimalMax decimalMax = field.getAnnotation(DecimalMax.class);
        if (decimalMax != null) {
            try {
                schema.put("maximum", new BigDecimal(decimalMax.value()));
            } catch (NumberFormatException ignored) {}
        }

        Size size = field.getAnnotation(Size.class);
        if (size != null) {
            String type = schema.path("type").asText();
            if ("string".equals(type)) {
                if (size.min() > 0) schema.put("minLength", size.min());
                if (size.max() < Integer.MAX_VALUE) schema.put("maxLength", size.max());
            } else if ("array".equals(type)) {
                if (size.min() > 0) schema.put("minItems", size.min());
                if (size.max() < Integer.MAX_VALUE) schema.put("maxItems", size.max());
            }
        }

        if (field.isAnnotationPresent(NotBlank.class) || field.isAnnotationPresent(NotEmpty.class)) {
            String type = schema.path("type").asText();
            if ("string".equals(type)) {
                schema.put("minLength", Math.max(schema.path("minLength").asInt(0), 1));
            } else if ("array".equals(type)) {
                schema.put("minItems", Math.max(schema.path("minItems").asInt(0), 1));
            }
        }
    }

    private void applyValidationConstraintsFromComponent(RecordComponent comp, Class<?> clazz, ObjectNode schema) {
        Field f = null;
        try {
            f = clazz.getDeclaredField(comp.getName());
        } catch (Exception ignored) {}

        Min min = comp.getAnnotation(Min.class) != null ? comp.getAnnotation(Min.class) : (f != null ? f.getAnnotation(Min.class) : null);
        if (min != null) schema.put("minimum", min.value());

        Max max = comp.getAnnotation(Max.class) != null ? comp.getAnnotation(Max.class) : (f != null ? f.getAnnotation(Max.class) : null);
        if (max != null) schema.put("maximum", max.value());

        DecimalMin decimalMin = comp.getAnnotation(DecimalMin.class) != null ? comp.getAnnotation(DecimalMin.class) : (f != null ? f.getAnnotation(DecimalMin.class) : null);
        if (decimalMin != null) {
            try { schema.put("minimum", new BigDecimal(decimalMin.value())); } catch (NumberFormatException ignored) {}
        }

        DecimalMax decimalMax = comp.getAnnotation(DecimalMax.class) != null ? comp.getAnnotation(DecimalMax.class) : (f != null ? f.getAnnotation(DecimalMax.class) : null);
        if (decimalMax != null) {
            try { schema.put("maximum", new BigDecimal(decimalMax.value())); } catch (NumberFormatException ignored) {}
        }

        Size size = comp.getAnnotation(Size.class) != null ? comp.getAnnotation(Size.class) : (f != null ? f.getAnnotation(Size.class) : null);
        if (size != null) {
            String type = schema.path("type").asText();
            if ("string".equals(type)) {
                if (size.min() > 0) schema.put("minLength", size.min());
                if (size.max() < Integer.MAX_VALUE) schema.put("maxLength", size.max());
            } else if ("array".equals(type)) {
                if (size.min() > 0) schema.put("minItems", size.min());
                if (size.max() < Integer.MAX_VALUE) schema.put("maxItems", size.max());
            }
        }

        boolean notBlank = comp.isAnnotationPresent(NotBlank.class) || (f != null && f.isAnnotationPresent(NotBlank.class));
        boolean notEmpty = comp.isAnnotationPresent(NotEmpty.class) || (f != null && f.isAnnotationPresent(NotEmpty.class));

        if (notBlank || notEmpty) {
            String type = schema.path("type").asText();
            if ("string".equals(type)) {
                schema.put("minLength", Math.max(schema.path("minLength").asInt(0), 1));
            } else if ("array".equals(type)) {
                schema.put("minItems", Math.max(schema.path("minItems").asInt(0), 1));
            }
        }
    }

    // ========================================================================
    // REQUIRED
    // ========================================================================

    private boolean isRequired(Field field) {
        return field.isAnnotationPresent(NotNull.class)
                || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class);
    }

    private boolean isRequiredComponent(RecordComponent comp, Class<?> clazz) {
        Field f = null;
        try {
            f = clazz.getDeclaredField(comp.getName());
        } catch (Exception ignored) {}

        return comp.isAnnotationPresent(NotNull.class)
                || comp.isAnnotationPresent(NotBlank.class)
                || comp.isAnnotationPresent(NotEmpty.class)
                || (f != null && (f.isAnnotationPresent(NotNull.class)
                || f.isAnnotationPresent(NotBlank.class)
                || f.isAnnotationPresent(NotEmpty.class)));
    }

    // ========================================================================
    // FIELD FILTERING
    // ========================================================================

    private List<Field> getRelevantFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic() || field.getName().startsWith("$")) {
                    continue;
                }
                result.add(field);
            }
            current = current.getSuperclass();
        }

        return result;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String definitionName(Class<?> clazz) {
        return clazz.getName().replace('.', '_').replace('$', '_');
    }

    private ObjectNode schemaString() {
        return objectMapper.createObjectNode().put("type", "string");
    }

    private ObjectNode schemaString(String format) {
        return objectMapper.createObjectNode().put("type", "string").put("format", format);
    }

    private ObjectNode reference(String definitionName) {
        return objectMapper.createObjectNode().put("$ref", "#/$defs/" + definitionName);
    }

    private ObjectNode asObjectNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("allOf", objectMapper.createArrayNode().add(node));
        return wrapper;
    }

    private JsonNode nullable(JsonNode node) {
        ObjectNode nullable = objectMapper.createObjectNode();
        ArrayNode anyOf = nullable.putArray("anyOf");
        anyOf.add(node);
        anyOf.add(objectMapper.createObjectNode().put("type", "null"));
        return nullable;
    }

    // ========================================================================
    // CONTEXT
    // ========================================================================

    private static final class SchemaContext {
        private final ObjectNode definitions;

        private SchemaContext() {
            this.definitions = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
    }
}
