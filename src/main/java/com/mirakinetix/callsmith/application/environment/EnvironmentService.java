package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.Environment;

import java.util.List;
import java.util.Optional;

public final class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(
            EnvironmentRepository environmentRepository
    ) {
        this.environmentRepository =
                environmentRepository;
    }

    public Environment save(
            Environment environment
    ) {
        return environmentRepository.save(
                environment
        );
    }

    public Optional<Environment> findById(
            String id
    ) {
        return environmentRepository.findById(
                id
        );
    }

    public List<Environment> findAll() {
        return environmentRepository.findAll();
    }

    public Optional<Environment> findActive() {
        return environmentRepository.findActive();
    }

    public void setActive(
            String id
    ) {
        environmentRepository.setActive(id);
    }

    public void clearActive() {
        environmentRepository.clearActive();
    }

    public void deleteById(
            String id
    ) {
        environmentRepository.deleteById(id);
    }
}