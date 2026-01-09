package com.agenttrust.platform.web.problem;

/**
 * RFC 7807 / RFC 9457 Problem Details media type constants.
 * <p>Problem Details responses MUST use {@code application/problem+json}.</p>
 */
public final class ProblemMediaTypes {

    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    private ProblemMediaTypes() {
        // utility class
    }
}
