package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.environment.EnvironmentVariableRepository;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteEnvironmentVariableRepository implements EnvironmentVariableRepository {
    private static final String COLUMNS =
            "id, environment_id, name, is_secret, protection_version, created_at, updated_at";
    private static final String READ = "SELECT " + COLUMNS
            + ", stored_value AS result_value FROM environment_variables";
    private static final String SUMMARY = "SELECT " + COLUMNS
            + ", CASE WHEN is_secret = 0 AND protection_version = 0 THEN stored_value END AS result_value"
            + " FROM environment_variables";

    private final SqliteDatabase database;
    private final SqliteSecretStorage secrets;

    public SqliteEnvironmentVariableRepository(SqliteDatabase database) {
        this(database, new SqliteSecretStorage(database));
    }

    public SqliteEnvironmentVariableRepository(SqliteDatabase database, SqliteSecretStorage secrets) {
        this.database = database;
        this.secrets = secrets;
    }

    @Override
    public EnvironmentVariable save(EnvironmentVariable variable) {
        String storedValue = variable.secret()
                ? secrets.protect(variable.id(), variable.environmentId(), variable.name(), variable.value())
                : variable.value();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO environment_variables (
                         id, environment_id, name, stored_value, is_secret,
                         protection_version, created_at, updated_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         environment_id = excluded.environment_id,
                         name = excluded.name,
                         stored_value = excluded.stored_value,
                         is_secret = excluded.is_secret,
                         protection_version = excluded.protection_version,
                         updated_at = excluded.updated_at
                     WHERE (environment_variables.is_secret = 0
                            AND environment_variables.protection_version = 0)
                        OR (environment_variables.is_secret = 1
                            AND environment_variables.protection_version = ?
                            AND excluded.is_secret = 1)
                     """)) {
            statement.setString(1, variable.id());
            statement.setString(2, variable.environmentId());
            statement.setString(3, variable.name());
            statement.setString(4, storedValue);
            statement.setInt(5, variable.secret() ? 1 : 0);
            statement.setInt(6, variable.secret() ? SecretCipher.VERSION : 0);
            statement.setString(7, variable.createdAt().toString());
            statement.setString(8, variable.updatedAt().toString());
            statement.setInt(9, SecretCipher.VERSION);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("This variable cannot be overwritten or converted to plaintext.");
            }
            return variable;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save environment variable. Check the workspace and variable name.");
        }
    }

    @Override
    public Optional<EnvironmentVariable> findById(String id) {
        return findOne(READ + " WHERE id = ?", id);
    }

    @Override
    public List<EnvironmentVariable> findByEnvironmentId(String environmentId) {
        return findByEnvironment(environmentId, false);
    }

    @Override
    public List<EnvironmentVariable> findSummariesByEnvironmentId(String environmentId) {
        return findByEnvironment(environmentId, true);
    }

    private List<EnvironmentVariable> findByEnvironment(String environmentId, boolean summary) {
        String sql = (summary ? SUMMARY : READ)
                + " WHERE environment_id = ? ORDER BY name COLLATE NOCASE, id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, environmentId);
            try (var rows = statement.executeQuery()) {
                List<EnvironmentVariable> variables = new ArrayList<>();
                while (rows.next()) {
                    variables.add(mapVariable(rows, summary));
                }
                return variables;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load environment variables.");
        }
    }

    @Override
    public Optional<EnvironmentVariable> findByName(String environmentId, String name) {
        return findOne(READ + " WHERE environment_id = ? AND name = ?", environmentId, name);
    }

    private Optional<EnvironmentVariable> findOne(String sql, String... arguments) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            bindStrings(statement, arguments);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapVariable(rows, false)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read environment variable.");
        }
    }

    private static void bindStrings(PreparedStatement statement, String[] arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setString(index + 1, arguments[index]);
        }
    }

    @Override
    public void deleteById(String id) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM environment_variables WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete environment variable.");
        }
    }

    private EnvironmentVariable mapVariable(ResultSet row, boolean summary) throws SQLException {
        String id = row.getString("id");
        String environmentId = row.getString("environment_id");
        String name = row.getString("name");
        int flag = SqliteSecretStorage.integer(row, "is_secret");
        int version = SqliteSecretStorage.integer(row, "protection_version");
        if (flag < 0 || flag > 1 || (flag == 0 && version != 0)) {
            throw new IllegalStateException("Invalid environment secret storage record.");
        }
        if (version != 0 && version != SecretCipher.VERSION) {
            throw new IllegalStateException("Unsupported protected secret version. Use a compatible Callsmith build.");
        }

        String value = row.getString("result_value");
        if (flag == 1 && !summary) {
            if (version == 0) {
                throw new IllegalStateException("Set up Secret Storage to migrate legacy secret variables.");
            }
            if (value == null) {
                throw new IllegalStateException("Invalid protected secret record.");
            }
            value = secrets.reveal(id, environmentId, name, value);
        }
        try {
            return new EnvironmentVariable(id, environmentId, name, value, flag == 1,
                    Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")));
        } catch (java.time.format.DateTimeParseException | NullPointerException exception) {
            throw new IllegalStateException("Invalid environment variable timestamps.");
        }
    }
}
