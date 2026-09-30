package com.mirakinetix.callsmith.domain.http;

import java.util.Map;

public record HttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body
) {
}