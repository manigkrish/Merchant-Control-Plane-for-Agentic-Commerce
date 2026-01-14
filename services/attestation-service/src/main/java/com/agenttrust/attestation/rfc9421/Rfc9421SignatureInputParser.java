package com.agenttrust.attestation.rfc9421;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Rfc9421SignatureInputParser {

  public record Parsed(String label, Rfc9421SignatureInput input) { }

  /**
   * Parses a raw Signature-Input header value.
   *
   * Constraints:
   * - Single signature label only. Multiple labels are rejected.
   * - Strict parsing: missing required params or malformed formats are rejected.
   *
   * Supported format (single label):
   *   sig1=("@authority" "@path" "@signature-params");created=...;expires=...;keyid="...";alg="...";nonce="...";tag="..."
   */
  public Parsed parseSingle(String signatureInputHeader) {
    if (signatureInputHeader == null || signatureInputHeader.trim().isEmpty()) {
      throw new IllegalArgumentException("Signature-Input is required");
    }

    String raw = signatureInputHeader.trim();

    // Reject multiple labels by checking for an unquoted comma at top-level.
    if (containsTopLevelComma(raw)) {
      throw new IllegalArgumentException("Multiple Signature-Input labels are not supported");
    }

    int eq = raw.indexOf('=');
    if (eq <= 0) {
      throw new IllegalArgumentException("Invalid Signature-Input format");
    }

    String label = raw.substring(0, eq).trim();
    if (label.isEmpty()) {
      throw new IllegalArgumentException("Signature label is required");
    }

    String rest = raw.substring(eq + 1).trim();
    if (!rest.startsWith("(")) {
      throw new IllegalArgumentException("Signature-Input must start with component list");
    }

    int closeParen = findMatchingParen(rest, 0);
    if (closeParen < 0) {
      throw new IllegalArgumentException("Unterminated component list");
    }

    String componentsPart = rest.substring(0, closeParen + 1);
    String paramsPart = rest.substring(closeParen + 1).trim();

    List<String> coveredComponents = parseComponentsList(componentsPart);
    Map<String, String> params = parseParams(paramsPart);

    String keyId = requireParam(params, "keyid");
    String alg = requireParam(params, "alg");
    long created = parseLongParam(params, "created");
    long expires = parseLongParam(params, "expires");
    String nonce = requireParam(params, "nonce");
    String tag = requireParam(params, "tag");

    Rfc9421SignatureInput input = new Rfc9421SignatureInput(
        label,
        coveredComponents,
        new Rfc9421SignatureInput.SignatureParams(keyId, alg, created, expires, nonce, tag)
    );

    return new Parsed(label, input);
  }

  private static boolean containsTopLevelComma(String s) {
    boolean inQuotes = false;
    int parenDepth = 0;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);

      if (c == '"' && !isEscaped(s, i)) {
        inQuotes = !inQuotes;
      } else if (!inQuotes) {
        if (c == '(') parenDepth++;
        if (c == ')') parenDepth = Math.max(0, parenDepth - 1);
        if (c == ',' && parenDepth == 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isEscaped(String s, int i) {
    int backslashes = 0;
    int j = i - 1;
    while (j >= 0 && s.charAt(j) == '\\') {
      backslashes++;
      j--;
    }
    return (backslashes % 2) == 1;
  }

  private static int findMatchingParen(String s, int openIndex) {
    int depth = 0;
    boolean inQuotes = false;

    for (int i = openIndex; i < s.length(); i++) {
      char c = s.charAt(i);

      if (c == '"' && !isEscaped(s, i)) {
        inQuotes = !inQuotes;
        continue;
      }
      if (inQuotes) continue;

      if (c == '(') depth++;
      if (c == ')') {
        depth--;
        if (depth == 0) return i;
      }
    }
    return -1;
  }

  private static List<String> parseComponentsList(String componentsPart) {
    // Expected: ("@authority" "@path" "@signature-params")
    String s = componentsPart.trim();
    if (!s.startsWith("(") || !s.endsWith(")")) {
      throw new IllegalArgumentException("Invalid component list");
    }

    String inner = s.substring(1, s.length() - 1).trim();
    if (inner.isEmpty()) {
      throw new IllegalArgumentException("Component list must not be empty");
    }

    List<String> components = new ArrayList<>();
    int i = 0;
    while (i < inner.length()) {
      while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
      if (i >= inner.length()) break;

      if (inner.charAt(i) != '"') {
        throw new IllegalArgumentException("Component must be a quoted string");
      }

      int end = findClosingQuote(inner, i);
      if (end < 0) {
        throw new IllegalArgumentException("Unterminated component quote");
      }

      String value = unescapeQuoted(inner.substring(i + 1, end));
      if (value.isBlank()) {
        throw new IllegalArgumentException("Component must not be blank");
      }
      components.add(value);

      i = end + 1;
    }

    return List.copyOf(components);
  }

  private static int findClosingQuote(String s, int startQuote) {
    for (int i = startQuote + 1; i < s.length(); i++) {
      if (s.charAt(i) == '"' && !isEscaped(s, i)) {
        return i;
      }
    }
    return -1;
  }

  private static String unescapeQuoted(String s) {
    return s.replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static Map<String, String> parseParams(String paramsPart) {
    // Expected: ;created=...;expires=...;keyid="...";alg="...";nonce="...";tag="..."
    Map<String, String> params = new HashMap<>();

    String s = paramsPart.trim();
    if (s.isEmpty()) {
      throw new IllegalArgumentException("Signature parameters are required");
    }

    int i = 0;
    while (i < s.length()) {
      if (s.charAt(i) != ';') {
        throw new IllegalArgumentException("Signature parameters must start with ';'");
      }
      i++;

      int keyStart = i;
      while (i < s.length() && s.charAt(i) != '=' && s.charAt(i) != ';') i++;
      if (i >= s.length() || s.charAt(i) != '=') {
        throw new IllegalArgumentException("Invalid signature parameter");
      }

      String key = s.substring(keyStart, i).trim().toLowerCase();
      if (key.isEmpty()) {
        throw new IllegalArgumentException("Parameter name is required");
      }
      i++; // skip '='

      String value;
      if (i < s.length() && s.charAt(i) == '"') {
        int end = findClosingQuote(s, i);
        if (end < 0) throw new IllegalArgumentException("Unterminated quoted parameter");
        value = unescapeQuoted(s.substring(i + 1, end));
        i = end + 1;
      } else {
        int valStart = i;
        while (i < s.length() && s.charAt(i) != ';') i++;
        value = s.substring(valStart, i).trim();
      }

      if (value.isEmpty()) {
        throw new IllegalArgumentException("Parameter value is required: " + key);
      }
      params.put(key, value);
    }

    return Map.copyOf(params);
  }

  private static String requireParam(Map<String, String> params, String key) {
    String v = params.get(key);
    if (v == null || v.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing required signature parameter: " + key);
    }
    return v;
  }

  private static long parseLongParam(Map<String, String> params, String key) {
    String v = requireParam(params, key);
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid numeric signature parameter: " + key);
    }
  }
}
