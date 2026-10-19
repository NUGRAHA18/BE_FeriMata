package com.fertimata.backend.common.util;

import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin helper around the application {@link ObjectMapper}.
 *
 * <p>Several entities persist a raw JSON document (telemetry payloads, command parameters, metadata)
 * so that the schema does not have to change every time the hardware team changes a payload. This
 * class keeps the conversion in one place and turns Jackson failures into a predictable result
 * instead of leaking parser exceptions into services.
 */
@Component
public class JsonSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Serialises a value to JSON, or returns {@code null} when the value itself is null. */
    public String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    /** Parses JSON text into a map, returning an empty map for null/blank input. */
    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
        return parsed == null ? Map.of() : Collections.unmodifiableMap(parsed);
    }

    public JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }

    public <T> T fromJson(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
