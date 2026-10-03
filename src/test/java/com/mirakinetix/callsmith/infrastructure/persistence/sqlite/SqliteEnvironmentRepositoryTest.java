package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteEnvironmentRepositoryTest {

    private SqliteDatabase database;
    private SqliteEnvironmentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path databasePath =
                Files.createTempFile(
                        "callsmith-environment-test-",
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
                new SqliteEnvironmentRepository(
                        database
                );
    }

    @Test
    void shouldSaveAndFindEnvironment() {
        Environment environment =
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                );

        repository.save(environment);

        var result =
                repository.findById("environment-1");

        assertTrue(result.isPresent());
        assertEquals(environment, result.get());
    }

    @Test
    void shouldReturnEmptyWhenEnvironmentDoesNotExist() {
        var result =
                repository.findById("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateExistingEnvironment() {
        Instant createdAt =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        Environment original =
                new Environment(
                        "environment-1",
                        "Development",
                        false,
                        createdAt,
                        createdAt
                );

        repository.save(original);

        Instant updatedAt =
                Instant.parse(
                        "2026-10-02T11:00:00Z"
                );

        Environment updated =
                new Environment(
                        "environment-1",
                        "Production",
                        true,
                        createdAt,
                        updatedAt
                );

        repository.save(updated);

        var result =
                repository.findById("environment-1");

        assertTrue(result.isPresent());
        assertEquals(updated, result.get());
    }

    @Test
    void shouldFindAllEnvironmentsInDeterministicOrder() {
        Environment zebra =
                createEnvironment(
                        "environment-1",
                        "Zebra",
                        false
                );

        Environment alpha =
                createEnvironment(
                        "environment-2",
                        "Alpha",
                        false
                );

        Environment beta =
                createEnvironment(
                        "environment-3",
                        "Beta",
                        false
                );

        repository.save(zebra);
        repository.save(alpha);
        repository.save(beta);

        List<Environment> environments =
                repository.findAll();

        assertEquals(
                List.of(
                        alpha,
                        beta,
                        zebra
                ),
                environments
        );
    }

    @Test
    void shouldReturnActiveEnvironment() {
        Environment development =
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                );

        Environment production =
                createEnvironment(
                        "environment-2",
                        "Production",
                        true
                );

        repository.save(development);
        repository.save(production);

        var active =
                repository.findActive();

        assertTrue(active.isPresent());
        assertEquals(
                production,
                active.get()
        );
    }

    @Test
    void shouldReturnEmptyWhenNoEnvironmentIsActive() {
        repository.save(
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                )
        );

        assertTrue(
                repository.findActive().isEmpty()
        );
    }

    @Test
    void shouldSwitchActiveEnvironment() {
        Environment development =
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                );

        Environment production =
                createEnvironment(
                        "environment-2",
                        "Production",
                        false
                );

        repository.save(development);
        repository.save(production);

        repository.setActive(
                "environment-1"
        );

        assertEquals(
                "environment-1",
                repository.findActive()
                        .orElseThrow()
                        .id()
        );

        repository.setActive(
                "environment-2"
        );

        assertEquals(
                "environment-2",
                repository.findActive()
                        .orElseThrow()
                        .id()
        );

        assertFalse(
                repository.findById(
                        "environment-1"
                ).orElseThrow().active()
        );

        assertTrue(
                repository.findById(
                        "environment-2"
                ).orElseThrow().active()
        );
    }

    @Test
    void shouldClearActiveEnvironment() {
        Environment development =
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                );

        Environment production =
                createEnvironment(
                        "environment-2",
                        "Production",
                        false
                );

        repository.save(development);
        repository.save(production);

        repository.setActive(
                "environment-2"
        );

        assertTrue(
                repository.findActive().isPresent()
        );

        repository.clearActive();

        assertTrue(
                repository.findActive().isEmpty()
        );

        assertFalse(
                repository.findById(
                        "environment-1"
                ).orElseThrow().active()
        );

        assertFalse(
                repository.findById(
                        "environment-2"
                ).orElseThrow().active()
        );
    }

    @Test
    void shouldRejectActivationOfMissingEnvironment() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> repository.setActive(
                                "missing"
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "Environment does not exist"
                        )
        );

        assertTrue(
                repository.findActive().isEmpty()
        );
    }

    @Test
    void shouldDeleteEnvironment() {
        Environment environment =
                createEnvironment(
                        "environment-1",
                        "Development",
                        false
                );

        repository.save(environment);

        repository.deleteById(
                "environment-1"
        );

        assertTrue(
                repository.findById(
                        "environment-1"
                ).isEmpty()
        );
    }

    private Environment createEnvironment(
            String id,
            String name,
            boolean active
    ) {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        return new Environment(
                id,
                name,
                active,
                timestamp,
                timestamp
        );
    }
}