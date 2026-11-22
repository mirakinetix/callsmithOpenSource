package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.util.List;
import java.util.Optional;

public interface EnvironmentVariableRepository {

    EnvironmentVariable save(
            EnvironmentVariable variable
    );

    Optional<EnvironmentVariable> findById(
            String id
    );

    List<EnvironmentVariable> findByEnvironmentId(
            String environmentId
    );

    Optional<EnvironmentVariable> findByName(
            String environmentId,
            String name
    );

    /** Returns names and display data; secret values are omitted. */
    List<EnvironmentVariable> findSummariesByEnvironmentId(String environmentId);

    void deleteById(String id);
}