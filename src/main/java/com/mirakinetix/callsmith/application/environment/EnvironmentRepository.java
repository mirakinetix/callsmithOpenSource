package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.Environment;

import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository {

    Environment save(Environment environment);

    Optional<Environment> findById(String id);

    List<Environment> findAll();

    Optional<Environment> findActive();

    void setActive(String id);

    void clearActive();

    void deleteById(String id);
}