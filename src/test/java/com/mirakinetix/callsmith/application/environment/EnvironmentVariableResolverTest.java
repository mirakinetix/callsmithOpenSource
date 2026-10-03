package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnvironmentVariableResolverTest {

    private final EnvironmentVariableRepository repository =
            mock(EnvironmentVariableRepository.class);

    private final EnvironmentVariableResolver resolver =
            new EnvironmentVariableResolver(repository);

    @Test
    void shouldResolveSingleVariable() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of(
                        createVariable(
                                "baseUrl",
                                "https://api.example.com"
                        )
                ));

        String result =
                resolver.resolve(
                        "{{baseUrl}}/users",
                        "env-1"
                );

        assertEquals(
                "https://api.example.com/users",
                result
        );
    }

    @Test
    void shouldResolveMultipleVariables() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of(
                        createVariable(
                                "host",
                                "api.example.com"
                        ),
                        createVariable(
                                "version",
                                "v1"
                        )
                ));

        String result =
                resolver.resolve(
                        "https://{{host}}/{{version}}/users",
                        "env-1"
                );

        assertEquals(
                "https://api.example.com/v1/users",
                result
        );
    }

    @Test
    void shouldResolveRepeatedVariable() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of(
                        createVariable(
                                "host",
                                "api.example.com"
                        )
                ));

        String result =
                resolver.resolve(
                        "{{host}}/{{host}}",
                        "env-1"
                );

        assertEquals(
                "api.example.com/api.example.com",
                result
        );
    }

    @Test
    void shouldLeaveUnknownVariableUnchanged() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of(
                        createVariable(
                                "host",
                                "api.example.com"
                        )
                ));

        String result =
                resolver.resolve(
                        "https://{{host}}/{{missing}}",
                        "env-1"
                );

        assertEquals(
                "https://api.example.com/{{missing}}",
                result
        );
    }

    @Test
    void shouldReturnOriginalValueWhenEnvironmentIdIsMissing() {
        String value =
                "https://{{host}}/users";

        String result =
                resolver.resolve(
                        value,
                        null
                );

        assertEquals(
                value,
                result
        );
    }

    @Test
    void shouldReturnOriginalValueWhenValueIsNull() {
        String result =
                resolver.resolve(
                        null,
                        "env-1"
                );

        assertEquals(
                null,
                result
        );
    }

    @Test
    void shouldReturnOriginalValueWhenValueIsEmpty() {
        String result =
                resolver.resolve(
                        "",
                        "env-1"
                );

        assertEquals(
                "",
                result
        );
    }

    @Test
    void shouldReturnOriginalValueWhenEnvironmentHasNoVariables() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of());

        String value =
                "https://{{host}}/users";

        String result =
                resolver.resolve(
                        value,
                        "env-1"
                );

        assertEquals(
                value,
                result
        );
    }

    @Test
    void shouldTrimVariableNameInsidePlaceholder() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of(
                        createVariable(
                                "host",
                                "api.example.com"
                        )
                ));

        String result =
                resolver.resolve(
                        "https://{{ host }}/users",
                        "env-1"
                );

        assertEquals(
                "https://api.example.com/users",
                result
        );
    }

    @Test
    void shouldNotResolveVariableFromDifferentEnvironment() {
        when(repository.findByEnvironmentId("env-1"))
                .thenReturn(List.of());

        String value =
                "https://{{host}}/users";

        String result =
                resolver.resolve(
                        value,
                        "env-1"
                );

        assertEquals(
                value,
                result
        );
    }

    private EnvironmentVariable createVariable(
            String name,
            String value
    ) {
        Instant now =
                Instant.parse(
                        "2026-10-02T00:00:00Z"
                );

        return new EnvironmentVariable(
                "variable-" + name,
                "env-1",
                name,
                value,
                false,
                now,
                now
        );
    }
}