package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class SqliteDatabase {

    private final Path databasePath;

    public SqliteDatabase(Path databasePath) {
        this.databasePath = databasePath;
    }

    public Connection openConnection() {
        try {
            Path parent =
                    databasePath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Connection connection =
                    DriverManager.getConnection(
                            "jdbc:sqlite:"
                                    + databasePath
            );

            configure(connection);

            return connection;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to open Callsmith database",
                    exception
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Unable to create Callsmith database directory",
                    exception
            );
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    private void configure(
            Connection connection
    ) throws SQLException {

        try (var statement =
                     connection.createStatement()) {

            statement.execute(
                    "PRAGMA foreign_keys = ON"
            );

            statement.execute(
                    "PRAGMA journal_mode = WAL"
            );

            statement.execute(
                    "PRAGMA busy_timeout = 5000"
            );
        }
    }
}