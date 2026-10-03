package com.mirakinetix.callsmith.domain.environment;

import java.time.Instant;

public record EnvironmentVariable(
        String id,
        String environmentId,
        String name,
        String value,
        boolean secret,
        Instant createdAt,
        Instant updatedAt
) {
}