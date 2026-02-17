package com.agenttrust.decision.clients;

/**
 * Used for safe pass-through of downstream errors (Problem Details).
 *
 * decision-service controller catches this and returns the exact status/content-type/body.
 */
public class DownstreamProblemException extends RuntimeException {

    private final int httpStatus;
    private final String contentType;
    private final byte[] bodyBytes;

    public DownstreamProblemException(int httpStatus, String contentType, byte[] bodyBytes) {
        super("Downstream error " + httpStatus);
        this.httpStatus = httpStatus;
        this.contentType = contentType;
        this.bodyBytes = (bodyBytes == null) ? new byte[0] : bodyBytes;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] bodyBytes() {
        return bodyBytes;
    }
}
