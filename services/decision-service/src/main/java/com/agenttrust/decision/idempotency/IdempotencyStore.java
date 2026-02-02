package com.agenttrust.decision.idempotency;

import com.agenttrust.decision.config.IdempotencyKeySupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
public class IdempotencyStore {

    private final StringRedisTemplate redis;
    private final IdempotencyKeySupport keySupport;
    private final IdempotencyRecordCodec codec;

    public IdempotencyStore(
            StringRedisTemplate redis,
            IdempotencyKeySupport keySupport,
            IdempotencyRecordCodec codec
    ) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keySupport = Objects.requireNonNull(keySupport, "keySupport");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Returns an idempotency hit if present and requestHash matches.
     *
     * If the same Idempotency-Key is reused for a different requestHash, throws IdempotencyConflictException.
     */
    public Optional<IdempotencyRecord> getIfPresentOrThrowConflict(
            String tenantId,
            String idempotencyKey,
            String requestHash
    ) {
        String redisKey = keySupport.redisKey(tenantId, idempotencyKey);
        String existing = redis.opsForValue().get(redisKey);
        if (existing == null) {
            return Optional.empty();
        }

        IdempotencyRecord record = codec.decode(existing);
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(tenantId, idempotencyKey);
        }
        return Optional.of(record);
    }

    /**
     * Stores the record if absent. If a record already exists:
     * - same requestHash: keep existing (first writer wins)
     * - different requestHash: throw conflict
     *
     * This is safe when multiple retries race.
     */
    public void putIfAbsentOrThrowConflict(
            String tenantId,
            String idempotencyKey,
            IdempotencyRecord record
    ) {
        Objects.requireNonNull(record, "record");

        String redisKey = keySupport.redisKey(tenantId, idempotencyKey);
        String json = codec.encode(record);

        boolean stored = Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(redisKey, json, Duration.ofSeconds(keySupport.ttlSeconds()))
        );

        if (stored) {
            return;
        }

        // Someone already stored it. Validate consistency.
        String existing = redis.opsForValue().get(redisKey);
        if (existing == null) {
            // Rare race: key expired or deleted between calls. Retry once.
            boolean storedRetry = Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(redisKey, json, Duration.ofSeconds(keySupport.ttlSeconds()))
            );
            if (!storedRetry) {
                // Fall through to conflict check
                existing = redis.opsForValue().get(redisKey);
            } else {
                return;
            }
        }

        if (existing == null) {
            // Still not available; be conservative.
            throw new IllegalStateException("Idempotency store unstable (redis key missing after setIfAbsent)");
        }

        IdempotencyRecord existingRecord = codec.decode(existing);
        if (!existingRecord.requestHash().equals(record.requestHash())) {
            throw new IdempotencyConflictException(tenantId, idempotencyKey);
        }
        // Same requestHash: keep existing record.
    }
}
