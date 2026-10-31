package com.fertimata.backend.common.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Wraps the app {@link ObjectMapper} for JSON columns (payloads, parameters, metadata); Jackson errors never leak. */
@Component
public class JsonSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

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

    /** Parses a JSON array, returning an empty list for null/blank input. */
    public List<Object> toList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Object> parsed = objectMapper.readValue(json, LIST_TYPE);
        return parsed == null ? List.of() : Collections.unmodifiableList(parsed);
    }

    public JsonNode readTree(String json) {
        return objectMapper.readTree(json);
    }

    public <T> T fromJson(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
