package com.mirakinetix.callsmith.infrastructure.persistence;

import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;

import java.sql.Connection;
import java.sql.SQLException;

public final class CallsmithDatabase {

    private final SqliteDatabase sqliteDatabase;
    private final MigrationRunner migrationRunner;

    public CallsmithDatabase(
            SqliteDatabase sqliteDatabase,
            MigrationRunner migrationRunner
    ) {
        this.sqliteDatabase = sqliteDatabase;
        this.migrationRunner = migrationRunner;
    }

    public void initialize() {
        try (Connection connection =
                     sqliteDatabase.openConnection()) {

            migrationRunner.migrate(connection);

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to initialize Callsmith database",
                    exception
            );
        }
    }

    public Connection openConnection() {
        return sqliteDatabase.openConnection();
    }
}