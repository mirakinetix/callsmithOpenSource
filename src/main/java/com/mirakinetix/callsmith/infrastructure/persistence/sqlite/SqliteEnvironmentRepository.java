package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.environment.EnvironmentRepository;
import com.mirakinetix.callsmith.domain.environment.Environment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteEnvironmentRepository
        implements EnvironmentRepository {

    private final SqliteDatabase database;

    public SqliteEnvironmentRepository(
            SqliteDatabase database
    ) {
        this.database = database;
    }

    @Override
    public Environment save(
            Environment environment
    ) {
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
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    is_active = excluded.is_active,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
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

            return environment;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save environment: "
                            + environment.id(),
                    exception
            );
        }
    }

    @Override
    public Optional<Environment> findById(
            String id
    ) {
        String sql =
                """
                SELECT
                    id,
                    name,
                    is_active,
                    created_at,
                    updated_at
                FROM environments
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
                        mapEnvironment(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find environment: "
                            + id,
                    exception
            );
        }
    }

    @Override
    public List<Environment> findAll() {
        String sql =
                """
                SELECT
                    id,
                    name,
                    is_active,
                    created_at,
                    updated_at
                FROM environments
                ORDER BY name COLLATE NOCASE, id
                """;

        List<Environment> environments =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            while (resultSet.next()) {
                environments.add(
                        mapEnvironment(resultSet)
                );
            }

            return environments;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load environments",
                    exception
            );
        }
    }

    @Override
    public Optional<Environment> findActive() {
        String sql =
                """
                SELECT
                    id,
                    name,
                    is_active,
                    created_at,
                    updated_at
                FROM environments
                WHERE is_active = 1
                ORDER BY id
                LIMIT 1
                """;

        try (Connection connection =
                     database.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                return Optional.empty();
            }

            return Optional.of(
                    mapEnvironment(resultSet)
            );

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find active environment",
                    exception
            );
        }
    }

    @Override
    public void setActive(
            String id
    ) {
        try (Connection connection =
                     database.openConnection()) {

            boolean previousAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                deactivateAll(connection);

                activateEnvironment(
                        connection,
                        id
                );

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();

                throw new IllegalStateException(
                        "Unable to activate environment: "
                                + id,
                        exception
                );

            } finally {
                connection.setAutoCommit(
                        previousAutoCommit
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to activate environment: "
                            + id,
                    exception
            );
        }
    }

    @Override
    public void clearActive() {
        try (Connection connection =
                     database.openConnection()) {

            boolean previousAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                deactivateAll(connection);

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();

                throw new IllegalStateException(
                        "Unable to clear active environment",
                        exception
                );

            } finally {
                connection.setAutoCommit(
                        previousAutoCommit
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to clear active environment",
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
                DELETE FROM environments
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
                    "Unable to delete environment: "
                            + id,
                    exception
            );
        }
    }

    private void deactivateAll(
            Connection connection
    ) throws SQLException {

        try (Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate(
                    """
                    UPDATE environments
                    SET is_active = 0
                    WHERE is_active = 1
                    """
            );
        }
    }

    private void activateEnvironment(
            Connection connection,
            String id
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             UPDATE environments
                             SET is_active = 1
                             WHERE id = ?
                             """
                     )) {

            statement.setString(
                    1,
                    id
            );

            int updatedRows =
                    statement.executeUpdate();

            if (updatedRows == 0) {
                throw new IllegalStateException(
                        "Environment does not exist: "
                                + id
                );
            }
        }
    }

    private Environment mapEnvironment(
            ResultSet resultSet
    ) throws SQLException {

        return new Environment(
                resultSet.getString("id"),
                resultSet.getString("name"),
                resultSet.getInt("is_active") == 1,
                Instant.parse(
                        resultSet.getString("created_at")
                ),
                Instant.parse(
                        resultSet.getString("updated_at")
                )
        );
    }
}