package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
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

class SqliteCollectionFolderRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteDatabase sqliteDatabase;
    private SqliteCollectionFolderRepository repository;

    @BeforeEach
    void setUp() {
        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        sqliteDatabase =
                new SqliteDatabase(databasePath);

        CallsmithDatabase database =
                new CallsmithDatabase(
                        sqliteDatabase,
                        new MigrationRunner()
                );

        database.initialize();

        repository =
                new SqliteCollectionFolderRepository(
                        sqliteDatabase
                );

        createCollection("collection-1");
        createCollection("collection-2");
    }

    @Test
    void shouldSaveAndFindFolder() {
        Instant createdAt =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        Instant updatedAt =
                Instant.parse(
                        "2026-10-02T14:05:00Z"
                );

        CollectionFolder folder =
                new CollectionFolder(
                        "folder-1",
                        "collection-1",
                        null,
                        "Test Folder",
                        0,
                        createdAt,
                        updatedAt
                );

        CollectionFolder saved =
                repository.save(folder);

        Optional<CollectionFolder> found =
                repository.findById("folder-1");

        assertEquals(
                folder,
                saved
        );

        assertTrue(found.isPresent());

        assertEquals(
                folder,
                found.orElseThrow()
        );
    }

    @Test
    void shouldSaveFolderWithParentFolder() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        CollectionFolder parent =
                new CollectionFolder(
                        "folder-parent",
                        "collection-1",
                        null,
                        "Parent Folder",
                        0,
                        timestamp,
                        timestamp
                );

        CollectionFolder child =
                new CollectionFolder(
                        "folder-child",
                        "collection-1",
                        "folder-parent",
                        "Child Folder",
                        0,
                        timestamp,
                        timestamp
                );

        repository.save(parent);
        repository.save(child);

        Optional<CollectionFolder> found =
                repository.findById("folder-child");

        assertTrue(found.isPresent());

        assertEquals(
                "folder-parent",
                found.orElseThrow().parentFolderId()
        );
    }

    @Test
    void shouldReturnEmptyWhenFolderDoesNotExist() {
        Optional<CollectionFolder> found =
                repository.findById(
                        "missing-folder"
                );

        assertFalse(found.isPresent());
    }

    @Test
    void shouldUpdateExistingFolder() {
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

        CollectionFolder original =
                new CollectionFolder(
                        "folder-1",
                        "collection-1",
                        null,
                        "Original Name",
                        0,
                        createdAt,
                        firstUpdatedAt
                );

        CollectionFolder updated =
                new CollectionFolder(
                        "folder-1",
                        "collection-1",
                        null,
                        "Updated Name",
                        2,
                        createdAt,
                        secondUpdatedAt
                );

        repository.save(original);
        repository.save(updated);

        Optional<CollectionFolder> found =
                repository.findById("folder-1");

        assertTrue(found.isPresent());

        assertEquals(
                updated,
                found.orElseThrow()
        );
    }

    @Test
    void shouldFindFoldersByCollectionInDeterministicOrder() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        repository.save(
                new CollectionFolder(
                        "folder-3",
                        "collection-1",
                        null,
                        "Zulu Folder",
                        2,
                        timestamp,
                        timestamp
                )
        );

        repository.save(
                new CollectionFolder(
                        "folder-2",
                        "collection-1",
                        null,
                        "Beta Folder",
                        1,
                        timestamp,
                        timestamp
                )
        );

        repository.save(
                new CollectionFolder(
                        "folder-1",
                        "collection-1",
                        null,
                        "Alpha Folder",
                        1,
                        timestamp,
                        timestamp
                )
        );

        repository.save(
                new CollectionFolder(
                        "folder-other",
                        "collection-2",
                        null,
                        "Other Collection Folder",
                        0,
                        timestamp,
                        timestamp
                )
        );

        List<CollectionFolder> folders =
                repository.findByCollectionId(
                        "collection-1"
                );

        assertEquals(
                3,
                folders.size()
        );

        assertEquals(
                "Alpha Folder",
                folders.get(0).name()
        );

        assertEquals(
                "Beta Folder",
                folders.get(1).name()
        );

        assertEquals(
                "Zulu Folder",
                folders.get(2).name()
        );
    }

    @Test
    void shouldDeleteFolder() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        repository.save(
                new CollectionFolder(
                        "folder-1",
                        "collection-1",
                        null,
                        "Test Folder",
                        0,
                        timestamp,
                        timestamp
                )
        );

        assertTrue(
                repository.findById("folder-1")
                        .isPresent()
        );

        repository.deleteById("folder-1");

        assertFalse(
                repository.findById("folder-1")
                        .isPresent()
        );
    }

    @Test
    void shouldRejectFolderForMissingCollection() {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T14:00:00Z"
                );

        CollectionFolder folder =
                new CollectionFolder(
                        "orphan-folder",
                        "missing-collection",
                        null,
                        "Orphan Folder",
                        0,
                        timestamp,
                        timestamp
                );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> repository.save(folder)
        );
    }

    private void createCollection(
            String collectionId
    ) {
        try (var connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO collections (
                                 id,
                                 name,
                                 created_at,
                                 updated_at
                             )
                             VALUES (?, ?, ?, ?)
                             """
                     )) {

            statement.setString(
                    1,
                    collectionId
            );

            statement.setString(
                    2,
                    "Test Collection"
            );

            statement.setString(
                    3,
                    "2026-10-02T14:00:00Z"
            );

            statement.setString(
                    4,
                    "2026-10-02T14:00:00Z"
            );

            statement.executeUpdate();

        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(
                    "Unable to create test collection",
                    exception
            );
        }
    }
}