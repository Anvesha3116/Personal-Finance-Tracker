package com.financetracker.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses a JSON object; anything missing, malformed or not an object gives an empty map. */
    static Map<String, Object> readObject(byte[] body) {
        if (body.length == 0) return new HashMap<>();
        try {
            return MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
