package com.mirakinetix.callsmith.domain.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record HttpRequest(String method, String url, Map<String, String> headers,
                          Map<String, String> queryParameters, String body, RequestOptions options) {
    public HttpRequest(String method, String url, Map<String, String> headers,
                       Map<String, String> queryParameters, String body) {
        this(method, url, headers, queryParameters, body, RequestOptions.DEFAULT);
    }

    public HttpRequest {
        headers = snapshot(headers);
        queryParameters = snapshot(queryParameters);
        Objects.requireNonNull(options);
    }

    private static Map<String, String> snapshot(Map<String, String> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override public String toString() { return "HttpRequest"; }
}
