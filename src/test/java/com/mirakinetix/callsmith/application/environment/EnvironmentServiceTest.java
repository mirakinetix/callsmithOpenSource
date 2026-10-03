package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.Environment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentServiceTest {

    private final EnvironmentRepository repository =
            mock(EnvironmentRepository.class);

    private final EnvironmentService service =
            new EnvironmentService(repository);

    @Test
    void shouldSaveEnvironment() {
        Environment environment =
                createEnvironment(
                        "env-1",
                        "Development"
                );

        when(repository.save(environment))
                .thenReturn(environment);

        Environment result =
                service.save(environment);

        assertSame(
                environment,
                result
        );

        verify(repository)
                .save(environment);
    }

    @Test
    void shouldFindEnvironmentById() {
        Environment environment =
                createEnvironment(
                        "env-1",
                        "Development"
                );

        when(repository.findById("env-1"))
                .thenReturn(Optional.of(environment));

        Optional<Environment> result =
                service.findById("env-1");

        assertEquals(
                Optional.of(environment),
                result
        );

        verify(repository)
                .findById("env-1");
    }

    @Test
    void shouldFindAllEnvironments() {
        Environment development =
                createEnvironment(
                        "env-1",
                        "Development"
                );

        Environment production =
                createEnvironment(
                        "env-2",
                        "Production"
                );

        List<Environment> environments =
                List.of(
                        development,
                        production
                );

        when(repository.findAll())
                .thenReturn(environments);

        List<Environment> result =
                service.findAll();

        assertEquals(
                environments,
                result
        );

        verify(repository)
                .findAll();
    }

    @Test
    void shouldFindActiveEnvironment() {
        Environment environment =
                createEnvironment(
                        "env-1",
                        "Development"
                );

        when(repository.findActive())
                .thenReturn(Optional.of(environment));

        Optional<Environment> result =
                service.findActive();

        assertEquals(
                Optional.of(environment),
                result
        );

        verify(repository)
                .findActive();
    }

    @Test
    void shouldSetActiveEnvironment() {
        service.setActive("env-1");

        verify(repository)
                .setActive("env-1");
    }

    @Test
    void shouldClearActiveEnvironment() {
        service.clearActive();

        verify(repository)
                .clearActive();
    }

    @Test
    void shouldDeleteEnvironment() {
        service.deleteById("env-1");

        verify(repository)
                .deleteById("env-1");
    }

    private Environment createEnvironment(
            String id,
            String name
    ) {
        Instant now =
                Instant.parse(
                        "2026-10-02T00:00:00Z"
                );

        return new Environment(
                id,
                name,
                false,
                now,
                now
        );
    }
}