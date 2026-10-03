package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteEnvironmentVariableRepositoryTest {

    private SqliteDatabase database;
    private SqliteEnvironmentVariableRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path databasePath =
                Files.createTempFile(
                        "callsmith-environment-variable-test-",
                        ".db"
                );

        database =
                new SqliteDatabase(databasePath);

        CallsmithDatabase callsmithDatabase =
                new CallsmithDatabase(
                        database,
                        new MigrationRunner()
                );

        callsmithDatabase.initialize();

        repository =
                new SqliteEnvironmentVariableRepository(
                        database
                );
    }

    @Test
    void shouldSaveAndFindVariable() {
        createEnvironment("environment-1");

        EnvironmentVariable variable =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://example.com",
                        false
                );

        repository.save(variable);

        var result =
                repository.findById("variable-1");

        assertTrue(result.isPresent());
        assertEquals(variable, result.get());
    }

    @Test
    void shouldReturnEmptyWhenVariableDoesNotExist() {
        var result =
                repository.findById("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateExistingVariable() {
        createEnvironment("environment-1");

        Instant createdAt =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        EnvironmentVariable original =
                new EnvironmentVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://dev.example.com",
                        false,
                        createdAt,
                        createdAt
                );

        repository.save(original);

        Instant updatedAt =
                Instant.parse(
                        "2026-10-02T11:00:00Z"
                );

        EnvironmentVariable updated =
                new EnvironmentVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://prod.example.com",
                        true,
                        createdAt,
                        updatedAt
                );

        repository.save(updated);

        var result =
                repository.findById("variable-1");

        assertTrue(result.isPresent());
        assertEquals(updated, result.get());
    }

    @Test
    void shouldFindVariablesByEnvironmentIdInDeterministicOrder() {
        createEnvironment("environment-1");
        createEnvironment("environment-2");

        EnvironmentVariable zebra =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "zebra",
                        "z",
                        false
                );

        EnvironmentVariable alpha =
                createVariable(
                        "variable-2",
                        "environment-1",
                        "alpha",
                        "a",
                        false
                );

        EnvironmentVariable beta =
                createVariable(
                        "variable-3",
                        "environment-1",
                        "beta",
                        "b",
                        false
                );

        EnvironmentVariable otherEnvironment =
                createVariable(
                        "variable-4",
                        "environment-2",
                        "other",
                        "value",
                        false
                );

        repository.save(zebra);
        repository.save(alpha);
        repository.save(beta);
        repository.save(otherEnvironment);

        List<EnvironmentVariable> variables =
                repository.findByEnvironmentId(
                        "environment-1"
                );

        assertEquals(
                List.of(
                        alpha,
                        beta,
                        zebra
                ),
                variables
        );
    }

    @Test
    void shouldFindVariableByNameWithinEnvironment() {
        createEnvironment("environment-1");
        createEnvironment("environment-2");

        EnvironmentVariable development =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://dev.example.com",
                        false
                );

        EnvironmentVariable production =
                createVariable(
                        "variable-2",
                        "environment-2",
                        "baseUrl",
                        "https://prod.example.com",
                        false
                );

        repository.save(development);
        repository.save(production);

        var result =
                repository.findByName(
                        "environment-1",
                        "baseUrl"
                );

        assertTrue(result.isPresent());
        assertEquals(
                development,
                result.get()
        );
    }

    @Test
    void shouldReturnEmptyWhenVariableNameDoesNotExist() {
        createEnvironment("environment-1");

        var result =
                repository.findByName(
                        "environment-1",
                        "missing"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPersistSecretFlag() {
        createEnvironment("environment-1");

        EnvironmentVariable secret =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "apiKey",
                        "secret-value",
                        true
                );

        repository.save(secret);

        EnvironmentVariable loaded =
                repository.findById("variable-1")
                        .orElseThrow();

        assertTrue(loaded.secret());
        assertEquals(
                "secret-value",
                loaded.value()
        );
    }

    @Test
    void shouldRejectDuplicateVariableNameWithinEnvironment() {
        createEnvironment("environment-1");

        EnvironmentVariable first =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://one.example.com",
                        false
                );

        EnvironmentVariable duplicate =
                createVariable(
                        "variable-2",
                        "environment-1",
                        "baseUrl",
                        "https://two.example.com",
                        false
                );

        repository.save(first);

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(duplicate)
        );
    }

    @Test
    void shouldAllowSameVariableNameInDifferentEnvironments() {
        createEnvironment("environment-1");
        createEnvironment("environment-2");

        EnvironmentVariable development =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://dev.example.com",
                        false
                );

        EnvironmentVariable production =
                createVariable(
                        "variable-2",
                        "environment-2",
                        "baseUrl",
                        "https://prod.example.com",
                        false
                );

        repository.save(development);
        repository.save(production);

        assertEquals(
                development,
                repository.findByName(
                        "environment-1",
                        "baseUrl"
                ).orElseThrow()
        );

        assertEquals(
                production,
                repository.findByName(
                        "environment-2",
                        "baseUrl"
                ).orElseThrow()
        );
    }

    @Test
    void shouldRejectVariableWithMissingEnvironment() {
        EnvironmentVariable variable =
                createVariable(
                        "variable-1",
                        "missing-environment",
                        "baseUrl",
                        "https://example.com",
                        false
                );

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(variable)
        );
    }

    @Test
    void shouldDeleteVariable() {
        createEnvironment("environment-1");

        EnvironmentVariable variable =
                createVariable(
                        "variable-1",
                        "environment-1",
                        "baseUrl",
                        "https://example.com",
                        false
                );

        repository.save(variable);

        repository.deleteById(
                "variable-1"
        );

        assertTrue(
                repository.findById(
                        "variable-1"
                ).isEmpty()
        );
    }

    private void createEnvironment(
            String id
    ) {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        Environment environment =
                new Environment(
                        id,
                        id,
                        false,
                        timestamp,
                        timestamp
                );

        String sql =
                """
                INSERT INTO environments (
                    id,
                    name,
                    is_active,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (var connection =
                     database.openConnection();
             var statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    environment.id()
            );

            statement.setString(
                    2,
                    environment.name()
            );

            statement.setInt(
                    3,
                    environment.active() ? 1 : 0
            );

            statement.setString(
                    4,
                    environment.createdAt().toString()
            );

            statement.setString(
                    5,
                    environment.updatedAt().toString()
            );

            statement.executeUpdate();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create test environment",
                    exception
            );
        }
    }

    private EnvironmentVariable createVariable(
            String id,
            String environmentId,
            String name,
            String value,
            boolean secret
    ) {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        return new EnvironmentVariable(
                id,
                environmentId,
                name,
                value,
                secret,
                timestamp,
                timestamp
        );
    }
}