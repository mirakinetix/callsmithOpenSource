package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.util.List;
import java.util.Optional;

public final class EnvironmentVariableService {

    private final EnvironmentVariableRepository variableRepository;

    public EnvironmentVariableService(
            EnvironmentVariableRepository variableRepository
    ) {
        this.variableRepository =
                variableRepository;
    }

    public EnvironmentVariable save(
            EnvironmentVariable variable
    ) {
        return variableRepository.save(
                variable
        );
    }

    public Optional<EnvironmentVariable> findById(
            String id
    ) {
        return variableRepository.findById(id);
    }

    public List<EnvironmentVariable> findByEnvironmentId(
            String environmentId
    ) {
        return variableRepository.findByEnvironmentId(
                environmentId
        );
    }

    public Optional<EnvironmentVariable> findByName(
            String environmentId,
            String name
    ) {
        return variableRepository.findByName(
                environmentId,
                name
        );
    }

    public void deleteById(
            String id
    ) {
        variableRepository.deleteById(id);
    }
}