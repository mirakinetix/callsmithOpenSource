package com.mirakinetix.callsmith.application.testing;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ResponseAssertion(Type type, String target, String expected) {
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{[^{}]+}}");
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key");
    public static final int MAX_PER_REQUEST = 32;

    public ResponseAssertion {
        Objects.requireNonNull(type);
        target = target == null ? "" : target;
        expected = expected == null ? "" : expected;
    }

    public enum Type {
        STATUS_EQUALS("Status equals"),
        STATUS_RANGE("Status range"),
        HEADER_EXISTS("Header exists"),
        HEADER_EQUALS("Header equals"),
        BODY_CONTAINS("Body contains"),
        BODY_EXCLUDES("Body excludes"),
        JSON_EXISTS("JSON property exists"),
        JSON_EQUALS("JSON property equals"),
        DURATION_BELOW("Duration below");

        private final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public void validate() {
        if (target.length() > 512 || expected.length() > 4096) {
            throw invalid("Assertion inputs exceed the allowed length.");
        }
        switch (type) {
            case STATUS_EQUALS -> status(expected);
            case STATUS_RANGE -> {
                if (status(target) > status(expected)) throw invalid("The lowest status must precede the highest.");
            }
            case HEADER_EXISTS, HEADER_EQUALS -> {
                if (!target.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw invalid("Enter a valid header name.");
                if (type == Type.HEADER_EQUALS && SENSITIVE_HEADERS.contains(target.toLowerCase(Locale.ROOT))
                        && !hasReference(expected)) {
                    throw invalid("Use a protected environment variable to compare this header.");
                }
            }
            case BODY_CONTAINS, BODY_EXCLUDES -> {
                if (expected.isEmpty()) throw invalid("Enter text to compare.");
            }
            case JSON_EXISTS, JSON_EQUALS -> {
                if (!target.startsWith("/") || target.matches(".*~(?![01]).*")) {
                    throw invalid("Enter a JSON Pointer such as /user/id.");
                }
                JsonPointer.compile(target);
                if (type == Type.JSON_EQUALS && !isReference(expected)) parseExpected(expected);
            }
            case DURATION_BELOW -> number(expected, 1, 300_000, "Enter a duration from 1 to 300000 ms.");
        }
    }

    static boolean isReference(String value) { return REFERENCE.matcher(value).matches(); }
    static boolean hasReference(String value) { return REFERENCE.matcher(value).find(); }

    static JsonNode parseExpected(String value) {
        try {
            JsonNode node = JSON.readTree(value);
            if (node != null && !node.isMissingNode()) return node;
        } catch (JsonProcessingException exception) {
            // Do not forward Jackson's input excerpts.
        }
        throw invalid("Enter one valid JSON value. Quote strings, or use one protected variable reference.");
    }

    static int status(String value) { return number(value, 100, 599, "Enter a status from 100 to 599."); }
    static int milliseconds(String value) {
        return number(value, 1, 300_000, "Enter a duration from 1 to 300000 ms.");
    }

    private static int number(String value, int low, int high, String error) {
        try {
            int parsed = Integer.parseInt(value.strip());
            if (parsed >= low && parsed <= high) return parsed;
        } catch (NumberFormatException ignored) {
        }
        throw invalid(error);
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    @Override public String toString() { return type.toString(); }
}
