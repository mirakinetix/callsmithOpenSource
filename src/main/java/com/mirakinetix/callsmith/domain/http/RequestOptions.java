package com.mirakinetix.callsmith.domain.http;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record RequestOptions(Duration timeout, Authentication authentication,
                             BodyFormat format, List<Field> fields, SelectedUpload upload) {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final RequestOptions DEFAULT =
            new RequestOptions(DEFAULT_TIMEOUT, Authentication.NONE, BodyFormat.RAW, List.of(), null);

    public RequestOptions {
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(authentication);
        Objects.requireNonNull(format);
        fields = List.copyOf(fields);
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Request timeout must be positive and at most 300 seconds.");
        }
    }

    public enum BodyFormat {
        RAW("Raw"), JSON("JSON"), TEXT("Plain text"), XML("XML"),
        FORM("URL-encoded form"), MULTIPART("Multipart form");

        private final String label;
        BodyFormat(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum AuthType {
        NONE("None"), BASIC("Basic"), BEARER("Bearer"),
        API_HEADER("API key in header"), API_QUERY("API key in query");

        private final String label;
        AuthType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Authentication(AuthType type, String name, String credential) {
        public static final Authentication NONE = new Authentication(AuthType.NONE, "", "");
        public Authentication {
            Objects.requireNonNull(type);
            Objects.requireNonNull(name);
            Objects.requireNonNull(credential);
        }
        @Override public String toString() { return "Authentication[" + type + "]"; }
    }

    public record Field(String name, String value) {
        @Override public String toString() { return "Form field"; }
    }

    @Override public String toString() { return "RequestOptions"; }
}
