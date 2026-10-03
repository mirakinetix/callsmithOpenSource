package com.mirakinetix.callsmith.domain.request;

import java.time.Instant;
import java.util.Map;

public record SavedRequest(
        String id,
        String collectionId,
        String folderId,
        String name,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, String> queryParameters,
        String body,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}