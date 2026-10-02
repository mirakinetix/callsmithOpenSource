package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.collection.CollectionFolderRepository;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteCollectionFolderRepository
        implements CollectionFolderRepository {

    private final SqliteDatabase database;

    public SqliteCollectionFolderRepository(
            SqliteDatabase database
    ) {
        this.database = database;
    }

    @Override
    public CollectionFolder save(
            CollectionFolder folder
    ) {
        String sql =
                """
                INSERT INTO collection_folders (
                    id,
                    collection_id,
                    parent_folder_id,
                    name,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    collection_id = excluded.collection_id,
                    parent_folder_id = excluded.parent_folder_id,
                    name = excluded.name,
                    sort_order = excluded.sort_order,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    folder.id()
            );

            statement.setString(
                    2,
                    folder.collectionId()
            );

            statement.setString(
                    3,
                    folder.parentFolderId()
            );

            statement.setString(
                    4,
                    folder.name()
            );

            statement.setInt(
                    5,
                    folder.sortOrder()
            );

            statement.setString(
                    6,
                    folder.createdAt().toString()
            );

            statement.setString(
                    7,
                    folder.updatedAt().toString()
            );

            statement.executeUpdate();

            return folder;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save collection folder: "
                            + folder.id(),
                    exception
            );
        }
    }

    @Override
    public Optional<CollectionFolder> findById(
            String id
    ) {
        String sql =
                """
                SELECT
                    id,
                    collection_id,
                    parent_folder_id,
                    name,
                    sort_order,
                    created_at,
                    updated_at
                FROM collection_folders
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
                        mapFolder(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find collection folder: "
                            + id,
                    exception
            );
        }
    }

    @Override
    public List<CollectionFolder> findByCollectionId(
            String collectionId
    ) {
        String sql =
                """
                SELECT
                    id,
                    collection_id,
                    parent_folder_id,
                    name,
                    sort_order,
                    created_at,
                    updated_at
                FROM collection_folders
                WHERE collection_id = ?
                ORDER BY sort_order, name COLLATE NOCASE, id
                """;

        List<CollectionFolder> folders =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, collectionId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    folders.add(
                            mapFolder(resultSet)
                    );
                }
            }

            return folders;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load collection folders for collection: "
                            + collectionId,
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
                DELETE FROM collection_folders
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
                    "Unable to delete collection folder: "
                            + id,
                    exception
            );
        }
    }

    private CollectionFolder mapFolder(
            ResultSet resultSet
    ) throws SQLException {

        return new CollectionFolder(
                resultSet.getString("id"),
                resultSet.getString("collection_id"),
                resultSet.getString("parent_folder_id"),
                resultSet.getString("name"),
                resultSet.getInt("sort_order"),
                Instant.parse(
                        resultSet.getString("created_at")
                ),
                Instant.parse(
                        resultSet.getString("updated_at")
                )
        );
    }
}