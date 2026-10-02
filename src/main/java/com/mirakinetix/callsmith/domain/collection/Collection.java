package com.mirakinetix.callsmith.domain.collection;

import java.time.Instant;

public record Collection(
        String id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}