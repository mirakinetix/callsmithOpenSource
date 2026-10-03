package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentVariableServiceTest {

    private final EnvironmentVariableRepository repository =
            mock(EnvironmentVariableRepository.class);

    private final EnvironmentVariableService service =
            new EnvironmentVariableService(repository);

    @Test
    void shouldSaveVariable() {
        EnvironmentVariable variable =
                createVariable(
                        "var-1",
                        "env-1",
                        "baseUrl",
                        "https://api.example.com"
                );

        when(repository.save(variable))
                .thenReturn(variable);

        EnvironmentVariable result =
                service.save(variable);

        assertSame(
                variable,
                result
        );

        verify(repository)
                .save(variable);
    }

    @Test
    void shouldFindVariableById() {
        EnvironmentVariable variable =
                createVariable(
                        "var-1",
                        "env-1",
                        "baseUrl",
                        "https://api.example.com"
                );

        when(repository.findById("var-1"))
                .thenReturn(Optional.of(variable));

        Optional<EnvironmentVariable> result =
                service.findById("var-1");

        assertEquals(
                Optional.of(variable),
                result
        );

        verify(repository)
                .findById("var-1");
    }

    @Test
    void shouldFindVariablesByEnvironmentId() {
        EnvironmentVariable baseUrl =
                createVariable(
                        "var-1",
                        "env-1",
                        "baseUrl",
                        "https://api.example.com"
                );

        EnvironmentVariable token =
                createVariable(
                        "var-2",
                        "env-1",
                        "token",
                        "secret-token"
                );

        List<EnvironmentVariable> variables =
                List.of(
                        baseUrl,
                        token
                );

        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(variables);

        List<EnvironmentVariable> result =
                service.findByEnvironmentId("env-1");

        assertEquals(
                variables,
                result
        );

        verify(repository)
                .findByEnvironmentId("env-1");
    }

    @Test
    void shouldFindVariableByName() {
        EnvironmentVariable variable =
                createVariable(
                        "var-1",
                        "env-1",
                        "baseUrl",
                        "https://api.example.com"
                );

        when(repository.findByName(
                "env-1",
                "baseUrl"
        )).thenReturn(
                Optional.of(variable)
        );

        Optional<EnvironmentVariable> result =
                service.findByName(
                        "env-1",
                        "baseUrl"
                );

        assertEquals(
                Optional.of(variable),
                result
        );

        verify(repository)
                .findByName(
                        "env-1",
                        "baseUrl"
                );
    }

    @Test
    void shouldDeleteVariable() {
        service.deleteById("var-1");

        verify(repository)
                .deleteById("var-1");
    }

    private EnvironmentVariable createVariable(
            String id,
            String environmentId,
            String name,
            String value
    ) {
        Instant now =
                Instant.parse(
                        "2026-10-02T00:00:00Z"
                );

        return new EnvironmentVariable(
                id,
                environmentId,
                name,
                value,
                false,
                now,
                now
        );
    }
}