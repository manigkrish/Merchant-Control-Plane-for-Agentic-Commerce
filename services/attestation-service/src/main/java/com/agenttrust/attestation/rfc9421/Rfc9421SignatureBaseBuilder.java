package com.agenttrust.attestation.rfc9421;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

public final class Rfc9421SignatureBaseBuilder {

  /**
   * Builds the canonical signature base for the given covered components.
   *
   * Supported derived components:
   * - @authority
   * - @path
   * - @signature-params
   *
   * Supported header component (when using the overload that supplies header value):
   * - content-digest
   *
   * Note: This builder remains intentionally small and strict; any unsupported covered component
   * fails closed with IllegalArgumentException.
   */
  public String build(String authority,
                      String path,
                      List<String> coveredComponents,
                      Rfc9421SignatureInput.SignatureParams params) {
    return build(authority, path, coveredComponents, params, null);
  }

  /**
   * Overload that can include the Content-Digest header value when "content-digest" is a covered component.
   *
   * @param contentDigestHeaderValue the exact Content-Digest header value (e.g. sha-256=:...:)
   */
  public String build(String authority,
                      String path,
                      List<String> coveredComponents,
                      Rfc9421SignatureInput.SignatureParams params,
                      String contentDigestHeaderValue) {

    if (isBlank(authority)) {
      throw new IllegalArgumentException("authority is required");
    }
    if (isBlank(path)) {
      throw new IllegalArgumentException("path is required");
    }
    Objects.requireNonNull(coveredComponents, "coveredComponents");
    Objects.requireNonNull(params, "params");

    StringBuilder sb = new StringBuilder();

    for (String component : coveredComponents) {
      if (isBlank(component)) {
        throw new IllegalArgumentException("covered component must not be blank");
      }

      String c = component.trim();
      String cl = c.toLowerCase(Locale.ROOT);

      if ("@authority".equals(cl)) {
        appendLine(sb, "\"@authority\": " + quote(normalizeAuthority(authority)));
      } else if ("@path".equals(cl)) {
        appendLine(sb, "\"@path\": " + quote(normalizePath(path)));
      } else if ("@signature-params".equals(cl)) {
        appendLine(sb, "\"@signature-params\": " + buildSignatureParams(coveredComponents, params));
      } else if ("content-digest".equals(cl)) {
        String v = requireNonBlank(contentDigestHeaderValue, "contentDigest");
        appendLine(sb, "\"content-digest\": " + quote(normalizeHeaderValue(v)));
      } else {
        throw new IllegalArgumentException("Unsupported covered component: " + component);
      }
    }

    // Strip trailing newline for a stable base
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
      sb.deleteCharAt(sb.length() - 1);
    }

    return sb.toString();
  }

  private static void appendLine(StringBuilder sb, String line) {
    sb.append(line).append('\n');
  }

  private static String buildSignatureParams(List<String> coveredComponents,
                                             Rfc9421SignatureInput.SignatureParams params) {

    // Reconstruct the covered component list exactly as tokens inside parentheses.
    // Example: ("@authority" "@path" "@signature-params");created=...;expires=...;keyid="...";alg="...";nonce="...";tag="..."
    StringJoiner comps = new StringJoiner(" ");
    for (String c : coveredComponents) {
      comps.add(quote(c));
    }

    StringBuilder sb = new StringBuilder();
    sb.append('(').append(comps).append(')');

    // Parameter ordering is deterministic (matches the required params order in config).
    sb.append(";created=").append(params.created());
    sb.append(";expires=").append(params.expires());
    sb.append(";keyid=").append(quote(params.keyId()));
    sb.append(";alg=").append(quote(params.alg()));
    sb.append(";nonce=").append(quote(params.nonce()));
    sb.append(";tag=").append(quote(params.tag()));

    return sb.toString();
  }

  private static String quote(String s) {
    // Minimal escaping: quotes and backslashes
    String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + escaped + "\"";
  }

  private static String normalizeAuthority(String authority) {
    return authority.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizePath(String path) {
    // Keep path exactly; just trim whitespace. Callers must pass only path (no scheme/host).
    return path.trim();
  }

  /**
   * Normalize header field value per our narrow needs:
   * - trim leading/trailing whitespace
   * - collapse internal whitespace to a single space
   */
  private static String normalizeHeaderValue(String value) {
    String s = value.trim();
    if (s.isEmpty()) {
      return s;
    }

    StringBuilder out = new StringBuilder(s.length());
    boolean prevWs = false;

    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (Character.isWhitespace(ch)) {
        if (!prevWs) {
          out.append(' ');
          prevWs = true;
        }
      } else {
        out.append(ch);
        prevWs = false;
      }
    }

    String normalized = out.toString().trim();
    if (normalized.length() > 2048) {
      throw new IllegalArgumentException("contentDigest is too large");
    }
    return normalized;
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required when covered component is present");
    }
    return value;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
