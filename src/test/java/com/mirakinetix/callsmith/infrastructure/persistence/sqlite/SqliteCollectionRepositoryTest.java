package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCollectionRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteCollectionRepository repository;

    @BeforeEach
    void setUp() {
        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        SqliteDatabase sqliteDatabase =
                new SqliteDatabase(databasePath);

        CallsmithDatabase database =
                new CallsmithDatabase(
                        sqliteDatabase,
                        new MigrationRunner()
                );

        database.initialize();

        repository =
                new SqliteCollectionRepository(
                        sqliteDatabase
                );
    }

    @Test
    void shouldSaveAndFindCollection() {
        Instant createdAt =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        Instant updatedAt =
                Instant.parse(
                        "2026-10-02T14:05:00Z"
                );

        Collection collection =
                new Collection(
                        "collection-1",
                        "Test Collection",
                        createdAt,
                        updatedAt
                );

        Collection saved =
                repository.save(collection);

        Optional<Collection> found =
                repository.findById("collection-1");

        assertEquals(
                collection,
                saved
        );

        assertTrue(found.isPresent());

        assertEquals(
                collection,
                found.orElseThrow()
        );
    }

    @Test
    void shouldReturnEmptyWhenCollectionDoesNotExist() {
        Optional<Collection> found =
                repository.findById(
                        "missing-collection"
                );

        assertFalse(found.isPresent());
    }

    @Test
    void shouldUpdateExistingCollection() {
        Instant createdAt =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        Instant firstUpdatedAt =
                Instant.parse(
                        "2026-10-02T14:05:00Z"
                );

        Instant secondUpdatedAt =
                Instant.parse(
                        "2026-10-02T14:10:00Z"
                );

        Collection original =
                new Collection(
                        "collection-1",
                        "Original Name",
                        createdAt,
                        firstUpdatedAt
                );

        Collection updated =
                new Collection(
                        "collection-1",
                        "Updated Name",
                        createdAt,
                        secondUpdatedAt
                );

        repository.save(original);
        repository.save(updated);

        Optional<Collection> found =
                repository.findById("collection-1");

        assertTrue(found.isPresent());

        assertEquals(
                updated,
                found.orElseThrow()
        );
    }

    @Test
    void shouldFindAllCollectionsInDeterministicOrder() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        repository.save(
                new Collection(
                        "collection-2",
                        "Zulu Collection",
                        timestamp,
                        timestamp
                )
        );

        repository.save(
                new Collection(
                        "collection-1",
                        "Alpha Collection",
                        timestamp,
                        timestamp
                )
        );

        repository.save(
                new Collection(
                        "collection-3",
                        "Beta Collection",
                        timestamp,
                        timestamp
                )
        );

        List<Collection> collections =
                repository.findAll();

        assertEquals(
                3,
                collections.size()
        );

        assertEquals(
                "Alpha Collection",
                collections.get(0).name()
        );

        assertEquals(
                "Beta Collection",
                collections.get(1).name()
        );

        assertEquals(
                "Zulu Collection",
                collections.get(2).name()
        );
    }

    @Test
    void shouldDeleteCollection() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        repository.save(
                new Collection(
                        "collection-1",
                        "Test Collection",
                        timestamp,
                        timestamp
                )
        );

        assertTrue(
                repository.findById("collection-1")
                        .isPresent()
        );

        repository.deleteById("collection-1");

        assertFalse(
                repository.findById("collection-1")
                        .isPresent()
        );
    }
}