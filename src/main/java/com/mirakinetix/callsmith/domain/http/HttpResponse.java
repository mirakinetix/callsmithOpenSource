package com.mirakinetix.callsmith.domain.http;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpResponse(int statusCode, String reasonPhrase, Map<String, String> headers,
                           String body, long elapsedMilliseconds, long sizeBytes, String url, byte[] bytes) {
    public HttpResponse(int statusCode, String reasonPhrase, Map<String, String> headers,
                        String body, long elapsedMilliseconds, long sizeBytes, String url) {
        this(statusCode, reasonPhrase, headers, body, elapsedMilliseconds, sizeBytes, url, null);
    }

    public HttpResponse {
        headers = Map.copyOf(headers);
        bytes = bytes == null ? null : bytes.clone();
    }

    @Override public Map<String, String> headers() {
        if (!hasHeader("Location") && !hasHeader("Content-Location")) return headers;

        Map<String, String> display = new LinkedHashMap<>(headers);
        display.replaceAll((name, value) -> name.equalsIgnoreCase("Location") || name.equalsIgnoreCase("Content-Location")
                ? "[hidden]" : value);
        return Map.copyOf(display);
    }

    public boolean hasHeader(String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    /** Tests the received value without providing access to display-redacted headers. */
    public boolean headerMatches(String name, String expected) {
        return headers.entrySet().stream()
                .anyMatch(header -> header.getKey().equalsIgnoreCase(name) && header.getValue().equals(expected));
    }

    @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
    public boolean binary() { return bytes != null && bytes.length != 0 && body == null; }
    public boolean hasBytes() { return bytes != null && bytes.length != 0; }

    public HttpResponse withoutUrl() {
        return new HttpResponse(statusCode, reasonPhrase, headers, body, elapsedMilliseconds, sizeBytes, null, bytes);
    }

    @Override public String toString() {
        return "HttpResponse[statusCode=" + statusCode + ", elapsedMilliseconds="
                + elapsedMilliseconds + ", sizeBytes=" + sizeBytes + "]";
    }
}
