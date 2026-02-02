package com.agenttrust.decision.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Encodes/decodes IdempotencyRecord for Redis storage.
 *
 * Uses the Spring-managed ObjectMapper (do NOT create new ObjectMapper instances).
 */
@Component
public class IdempotencyRecordCodec {

    private final ObjectMapper objectMapper;

    public IdempotencyRecordCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String encode(IdempotencyRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode idempotency record", e);
        }
    }

    public IdempotencyRecord decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("idempotency record json must not be blank");
        }
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode idempotency record", e);
        }
    }
}
