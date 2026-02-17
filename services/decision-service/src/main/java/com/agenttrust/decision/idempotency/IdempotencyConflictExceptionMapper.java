package com.agenttrust.decision.idempotency;

import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

@RestControllerAdvice
public class IdempotencyConflictExceptionMapper {

    private static final MediaType PROBLEM_JSON =
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<byte[]> handle(IdempotencyConflictException ex) {
        // Keep it minimal and stable. Do NOT echo back tokens or request bodies.
        String json = """
                {"type":"about:blank","title":"Conflict","status":409,"detail":"Idempotency-Key reuse conflict","errorCode":"IDEMPOTENCY_KEY_REUSE_CONFLICT"}
                """;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }
}
