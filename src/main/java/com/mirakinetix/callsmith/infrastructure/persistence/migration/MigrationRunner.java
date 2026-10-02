package com.mirakinetix.callsmith.infrastructure.persistence.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

public final class MigrationRunner {

    private static final String MIGRATION_TABLE =
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                applied_at TEXT NOT NULL
            )
            """;

    public void migrate(Connection connection) {
        try {
            boolean previousAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                createMigrationTable(connection);

                for (Migration migration :
                        loadMigrations()) {

                    if (isApplied(
                            connection,
                            migration.version()
                    )) {
                        continue;
                    }

                    applyMigration(
                            connection,
                            migration
                    );
                }

                connection.commit();

            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;

            } catch (SQLException exception) {
                connection.rollback();

                throw new IllegalStateException(
                        "Database migration failed",
                        exception
                );

            } finally {
                connection.setAutoCommit(
                        previousAutoCommit
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Database migration failed",
                    exception
            );
        }
    }

    private void createMigrationTable(
            Connection connection
    ) throws SQLException {

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(
                    MIGRATION_TABLE
            );
        }
    }

    private boolean isApplied(
            Connection connection,
            int version
    ) throws SQLException {

        try (var statement =
                     connection.prepareStatement(
                             """
                             SELECT 1
                             FROM schema_migrations
                             WHERE version = ?
                             """
                     )) {

            statement.setInt(1, version);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private void applyMigration(
            Connection connection,
            Migration migration
    ) throws SQLException {

        String resourcePath =
                "/db/migration/"
                        + migration.resourceName();

        String sql =
                readResource(resourcePath);

        executeMigration(
                connection,
                sql
        );

        try (var statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO schema_migrations (
                                 version,
                                 name,
                                 applied_at
                             )
                             VALUES (?, ?, ?)
                             """
                     )) {

            statement.setInt(
                    1,
                    migration.version()
            );

            statement.setString(
                    2,
                    migration.resourceName()
            );

            statement.setString(
                    3,
                    Instant.now().toString()
            );

            statement.executeUpdate();
        }
    }

    private void executeMigration(
            Connection connection,
            String sql
    ) throws SQLException {

        String trimmedSql =
                sql.trim();

        if (trimmedSql.isEmpty()) {
            throw new IllegalStateException(
                    "Migration contains no SQL"
            );
        }

        if (trimmedSql.contains(";")) {
            throw new IllegalStateException(
                    "Migration must contain exactly one SQL statement"
            );
        }

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(trimmedSql);
        }
    }

    private List<Migration> loadMigrations() {
        return List.of(
                new Migration(
                        1,
                        "V1__initial_schema.sql"
                ),
                new Migration(
                        2,
                        "V2__create_collection_folders.sql"
                ),
                new Migration(
                        3,
                        "V3__create_saved_requests.sql"
                ),
                new Migration(
                        4,
                        "V4__create_environments.sql"
                ),
                new Migration(
                        5,
                        "V5__create_environment_variables.sql"
                ),
                new Migration(
                        6,
                        "V6__create_collection_folders_collection_index.sql"
                ),
                new Migration(
                        7,
                        "V7__create_collection_folders_parent_index.sql"
                ),
                new Migration(
                        8,
                        "V8__create_saved_requests_collection_index.sql"
                ),
                new Migration(
                        9,
                        "V9__create_saved_requests_folder_index.sql"
                ),
                new Migration(
                        10,
                        "V10__create_environment_variables_environment_index.sql"
                )
        );
    }

    private String readResource(
            String resourcePath
    ) {
        try (InputStream inputStream =
                     MigrationRunner.class
                             .getResourceAsStream(
                                     resourcePath
                             )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Migration resource not found: "
                                + resourcePath
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read migration: "
                            + resourcePath,
                    exception
            );
        }
    }

    private record Migration(
            int version,
            String resourceName
    ) {
    }
}