package com.mirakinetix.callsmith.infrastructure.persistence.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JacksonJsonSerializer {

    private static final TypeReference<LinkedHashMap<String, String>>
            STRING_MAP_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public JacksonJsonSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public String serializeStringMap(
            Map<String, String> values
    ) {
        try {
            return objectMapper.writeValueAsString(
                    values == null
                            ? Map.of()
                            : values
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize JSON map",
                    exception
            );
        }
    }

    public Map<String, String> deserializeStringMap(
            String json
    ) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    STRING_MAP_TYPE
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to deserialize JSON map",
                    exception
            );
        }
    }
}