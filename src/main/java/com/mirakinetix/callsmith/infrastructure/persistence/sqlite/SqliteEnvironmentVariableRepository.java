package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.environment.EnvironmentVariableRepository;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteEnvironmentVariableRepository
        implements EnvironmentVariableRepository {

    private final SqliteDatabase database;

    public SqliteEnvironmentVariableRepository(
            SqliteDatabase database
    ) {
        this.database = database;
    }

    @Override
    public EnvironmentVariable save(
            EnvironmentVariable variable
    ) {
        String sql =
                """
                INSERT INTO environment_variables (
                    id,
                    environment_id,
                    name,
                    value,
                    is_secret,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    environment_id = excluded.environment_id,
                    name = excluded.name,
                    value = excluded.value,
                    is_secret = excluded.is_secret,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    variable.id()
            );

            statement.setString(
                    2,
                    variable.environmentId()
            );

            statement.setString(
                    3,
                    variable.name()
            );

            statement.setString(
                    4,
                    variable.value()
            );

            statement.setInt(
                    5,
                    variable.secret() ? 1 : 0
            );

            statement.setString(
                    6,
                    variable.createdAt().toString()
            );

            statement.setString(
                    7,
                    variable.updatedAt().toString()
            );

            statement.executeUpdate();

            return variable;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save environment variable: "
                            + variable.id(),
                    exception
            );
        }
    }

    @Override
    public Optional<EnvironmentVariable> findById(
            String id
    ) {
        String sql =
                """
                SELECT
                    id,
                    environment_id,
                    name,
                    value,
                    is_secret,
                    created_at,
                    updated_at
                FROM environment_variables
                WHERE id = ?
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    id
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapVariable(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find environment variable: "
                            + id,
                    exception
            );
        }
    }

    @Override
    public List<EnvironmentVariable> findByEnvironmentId(
            String environmentId
    ) {
        String sql =
                """
                SELECT
                    id,
                    environment_id,
                    name,
                    value,
                    is_secret,
                    created_at,
                    updated_at
                FROM environment_variables
                WHERE environment_id = ?
                ORDER BY name COLLATE NOCASE, id
                """;

        List<EnvironmentVariable> variables =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    environmentId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    variables.add(
                            mapVariable(resultSet)
                    );
                }
            }

            return variables;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load environment variables for environment: "
                            + environmentId,
                    exception
            );
        }
    }

    @Override
    public Optional<EnvironmentVariable> findByName(
            String environmentId,
            String name
    ) {
        String sql =
                """
                SELECT
                    id,
                    environment_id,
                    name,
                    value,
                    is_secret,
                    created_at,
                    updated_at
                FROM environment_variables
                WHERE environment_id = ?
                  AND name = ?
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    environmentId
            );

            statement.setString(
                    2,
                    name
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapVariable(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find environment variable: "
                            + name,
                    exception
            );
        }
    }

    @Override
    public void deleteById(
            String id
    ) {
        String sql =
                """
                DELETE FROM environment_variables
                WHERE id = ?
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    id
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to delete environment variable: "
                            + id,
                    exception
            );
        }
    }

    private EnvironmentVariable mapVariable(
            ResultSet resultSet
    ) throws SQLException {

        return new EnvironmentVariable(
                resultSet.getString("id"),
                resultSet.getString("environment_id"),
                resultSet.getString("name"),
                resultSet.getString("value"),
                resultSet.getInt("is_secret") == 1,
                Instant.parse(
                        resultSet.getString("created_at")
                ),
                Instant.parse(
                        resultSet.getString("updated_at")
                )
        );
    }
}