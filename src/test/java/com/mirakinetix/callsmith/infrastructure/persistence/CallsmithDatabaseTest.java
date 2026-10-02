package com.mirakinetix.callsmith.infrastructure.persistence;

import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallsmithDatabaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldInitializeDatabaseAndApplySchema()
            throws SQLException {

        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        CallsmithDatabase database =
                createDatabase(databasePath);

        database.initialize();

        try (Connection connection =
                     database.openConnection()) {

            assertTableExists(
                    connection,
                    "schema_migrations"
            );

            assertTableExists(
                    connection,
                    "collections"
            );

            assertTableExists(
                    connection,
                    "collection_folders"
            );

            assertTableExists(
                    connection,
                    "saved_requests"
            );

            assertTableExists(
                    connection,
                    "environments"
            );

            assertTableExists(
                    connection,
                    "environment_variables"
            );
        }
    }

    @Test
    void shouldApplyMigrationOnlyOnce()
            throws SQLException {

        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        CallsmithDatabase database =
                createDatabase(databasePath);

        database.initialize();

        int migrationCountAfterFirstInitialization;

        try (Connection connection =
                     database.openConnection();
             var statement =
                     connection.createStatement();
             var resultSet =
                     statement.executeQuery(
                             """
                             SELECT COUNT(*)
                             FROM schema_migrations
                             """
                     )) {

            resultSet.next();

            migrationCountAfterFirstInitialization =
                    resultSet.getInt(1);
        }

        database.initialize();

        try (Connection connection =
                     database.openConnection();
             var statement =
                     connection.createStatement();
             var resultSet =
                     statement.executeQuery(
                             """
                             SELECT COUNT(*)
                             FROM schema_migrations
                             """
                     )) {

            resultSet.next();

            assertEquals(
                    10,
                    migrationCountAfterFirstInitialization
            );

            assertEquals(
                    migrationCountAfterFirstInitialization,
                    resultSet.getInt(1)
            );
        }
    }

    @Test
    void shouldEnforceForeignKeys()
            throws SQLException {

        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        CallsmithDatabase database =
                createDatabase(databasePath);

        database.initialize();

        try (Connection connection =
                     database.openConnection();
             var statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO collection_folders (
                                 id,
                                 collection_id,
                                 name,
                                 sort_order,
                                 created_at,
                                 updated_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?)
                             """
                     )) {

            statement.setString(
                    1,
                    "orphan-folder"
            );

            statement.setString(
                    2,
                    "missing-collection"
            );

            statement.setString(
                    3,
                    "Orphan Folder"
            );

            statement.setInt(
                    4,
                    0
            );

            statement.setString(
                    5,
                    "2026-10-02T00:00:00Z"
            );

            statement.setString(
                    6,
                    "2026-10-02T00:00:00Z"
            );

            assertThrows(
                    SQLException.class,
                    statement::executeUpdate
            );
        }
    }

    @Test
    void shouldAllowValidForeignKeyRelationship()
            throws SQLException {

        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        CallsmithDatabase database =
                createDatabase(databasePath);

        database.initialize();

        try (Connection connection =
                     database.openConnection();
             var collectionStatement =
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
                     );
             var folderStatement =
                     connection.prepareStatement(
                             """
                             INSERT INTO collection_folders (
                                 id,
                                 collection_id,
                                 name,
                                 sort_order,
                                 created_at,
                                 updated_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?)
                             """
                     )) {

            collectionStatement.setString(
                    1,
                    "collection-1"
            );

            collectionStatement.setString(
                    2,
                    "Test Collection"
            );

            collectionStatement.setString(
                    3,
                    "2026-10-02T00:00:00Z"
            );

            collectionStatement.setString(
                    4,
                    "2026-10-02T00:00:00Z"
            );

            assertEquals(
                    1,
                    collectionStatement.executeUpdate()
            );

            folderStatement.setString(
                    1,
                    "folder-1"
            );

            folderStatement.setString(
                    2,
                    "collection-1"
            );

            folderStatement.setString(
                    3,
                    "Test Folder"
            );

            folderStatement.setInt(
                    4,
                    0
            );

            folderStatement.setString(
                    5,
                    "2026-10-02T00:00:00Z"
            );

            folderStatement.setString(
                    6,
                    "2026-10-02T00:00:00Z"
            );

            assertEquals(
                    1,
                    folderStatement.executeUpdate()
            );
        }
    }

    private CallsmithDatabase createDatabase(
            Path databasePath
    ) {
        SqliteDatabase sqliteDatabase =
                new SqliteDatabase(databasePath);

        MigrationRunner migrationRunner =
                new MigrationRunner();

        return new CallsmithDatabase(
                sqliteDatabase,
                migrationRunner
        );
    }

    private void assertTableExists(
            Connection connection,
            String tableName
    ) throws SQLException {

        try (var statement =
                     connection.prepareStatement(
                             """
                             SELECT COUNT(*)
                             FROM sqlite_master
                             WHERE type = 'table'
                               AND name = ?
                             """
                     )) {

            statement.setString(
                    1,
                    tableName
            );

            try (var resultSet =
                         statement.executeQuery()) {

                resultSet.next();

                assertNotNull(resultSet);

                assertEquals(
                        1,
                        resultSet.getInt(1),
                        "Expected table to exist: "
                                + tableName
                );
            }
        }
    }
}