package com.mirakinetix.callsmith.domain.collection.importation;

import java.util.Map;

public record ImportedRequest(
        String name,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body
) {
}