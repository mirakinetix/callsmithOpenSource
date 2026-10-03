package com.mirakinetix.callsmith.domain.environment;

import java.time.Instant;

public record Environment(
        String id,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}