package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.collection.CollectionRepository;
import com.mirakinetix.callsmith.domain.collection.Collection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteCollectionRepository
        implements CollectionRepository {

    private final SqliteDatabase database;

    public SqliteCollectionRepository(
            SqliteDatabase database
    ) {
        this.database = database;
    }

    @Override
    public Collection save(
            Collection collection
    ) {
        String sql =
                """
                INSERT INTO collections (
                    id,
                    name,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    collection.id()
            );

            statement.setString(
                    2,
                    collection.name()
            );

            statement.setString(
                    3,
                    collection.createdAt().toString()
            );

            statement.setString(
                    4,
                    collection.updatedAt().toString()
            );

            statement.executeUpdate();

            return collection;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save collection: "
                            + collection.id(),
                    exception
            );
        }
    }

    @Override
    public Optional<Collection> findById(
            String id
    ) {
        String sql =
                """
                SELECT
                    id,
                    name,
                    created_at,
                    updated_at
                FROM collections
                WHERE id = ?
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, id);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapCollection(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find collection: " + id,
                    exception
            );
        }
    }

    @Override
    public List<Collection> findAll() {
        String sql =
                """
                SELECT
                    id,
                    name,
                    created_at,
                    updated_at
                FROM collections
                ORDER BY name COLLATE NOCASE, id
                """;

        List<Collection> collections =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            while (resultSet.next()) {
                collections.add(
                        mapCollection(resultSet)
                );
            }

            return collections;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load collections",
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
                DELETE FROM collections
                WHERE id = ?
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, id);
            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to delete collection: " + id,
                    exception
            );
        }
    }

    private Collection mapCollection(
            ResultSet resultSet
    ) throws SQLException {

        return new Collection(
                resultSet.getString("id"),
                resultSet.getString("name"),
                Instant.parse(
                        resultSet.getString("created_at")
                ),
                Instant.parse(
                        resultSet.getString("updated_at")
                )
        );
    }
}