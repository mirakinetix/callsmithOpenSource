package com.mirakinetix.callsmith.domain.collection;

import java.time.Instant;

public record CollectionFolder(
        String id,
        String collectionId,
        String parentFolderId,
        String name,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}