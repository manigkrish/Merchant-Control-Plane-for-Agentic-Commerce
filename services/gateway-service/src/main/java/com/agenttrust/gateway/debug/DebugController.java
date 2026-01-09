package com.agenttrust.gateway.debug;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint-1 verification endpoint.
 * This proves Problem Details + correlation/trace behavior is correctly wired.
 *
 * IMPORTANT: This endpoint must not ship to production.
 * We will remove or protect it in Sprint 2.
 */
@RestController
public class DebugController {

    @GetMapping("/__debug/problem")
    public String problem() {
        throw new IllegalArgumentException("Demonstration error: invalid input");
    }
}
