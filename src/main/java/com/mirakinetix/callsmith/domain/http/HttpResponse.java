package com.mirakinetix.callsmith.domain.http;

import java.util.Map;

public record HttpResponse(
        int statusCode,
        String reasonPhrase,
        Map<String, String> headers,
        String body,
        long elapsedMilliseconds,
        long sizeBytes,
        String url
) {
}