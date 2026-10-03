package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.request.SavedRequestRepository;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteSavedRequestRepository
        implements SavedRequestRepository {

    private final SqliteDatabase database;
    private final JacksonJsonSerializer jsonSerializer;

    public SqliteSavedRequestRepository(
            SqliteDatabase database,
            JacksonJsonSerializer jsonSerializer
    ) {
        this.database = database;
        this.jsonSerializer = jsonSerializer;
    }

    @Override
    public SavedRequest save(
            SavedRequest request
    ) {
        String sql =
                """
                INSERT INTO saved_requests (
                    id,
                    collection_id,
                    folder_id,
                    name,
                    method,
                    url,
                    headers_json,
                    query_parameters_json,
                    body,
                    sort_order,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    collection_id = excluded.collection_id,
                    folder_id = excluded.folder_id,
                    name = excluded.name,
                    method = excluded.method,
                    url = excluded.url,
                    headers_json = excluded.headers_json,
                    query_parameters_json = excluded.query_parameters_json,
                    body = excluded.body,
                    sort_order = excluded.sort_order,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    request.id()
            );

            statement.setString(
                    2,
                    request.collectionId()
            );

            statement.setString(
                    3,
                    request.folderId()
            );

            statement.setString(
                    4,
                    request.name()
            );

            statement.setString(
                    5,
                    request.method()
            );

            statement.setString(
                    6,
                    request.url()
            );

            statement.setString(
                    7,
                    jsonSerializer.serializeStringMap(
                            request.headers()
                    )
            );

            statement.setString(
                    8,
                    jsonSerializer.serializeStringMap(
                            request.queryParameters()
                    )
            );

            statement.setString(
                    9,
                    request.body()
            );

            statement.setInt(
                    10,
                    request.sortOrder()
            );

            statement.setString(
                    11,
                    request.createdAt().toString()
            );

            statement.setString(
                    12,
                    request.updatedAt().toString()
            );

            statement.executeUpdate();

            return request;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save request: "
                            + request.id(),
                    exception
            );
        }
    }

    @Override
    public Optional<SavedRequest> findById(
            String id
    ) {
        String sql =
                """
                SELECT
                    id,
                    collection_id,
                    folder_id,
                    name,
                    method,
                    url,
                    headers_json,
                    query_parameters_json,
                    body,
                    sort_order,
                    created_at,
                    updated_at
                FROM saved_requests
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
                        mapSavedRequest(resultSet)
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find saved request: "
                            + id,
                    exception
            );
        }
    }

    @Override
    public List<SavedRequest> findByCollectionId(
            String collectionId
    ) {
        String sql =
                """
                SELECT
                    id,
                    collection_id,
                    folder_id,
                    name,
                    method,
                    url,
                    headers_json,
                    query_parameters_json,
                    body,
                    sort_order,
                    created_at,
                    updated_at
                FROM saved_requests
                WHERE collection_id = ?
                ORDER BY sort_order, name COLLATE NOCASE, id
                """;

        List<SavedRequest> requests =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    collectionId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    requests.add(
                            mapSavedRequest(resultSet)
                    );
                }
            }

            return requests;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load saved requests for collection: "
                            + collectionId,
                    exception
            );
        }
    }

    @Override
    public List<SavedRequest> findByFolderId(
            String folderId
    ) {
        String sql =
                """
                SELECT
                    id,
                    collection_id,
                    folder_id,
                    name,
                    method,
                    url,
                    headers_json,
                    query_parameters_json,
                    body,
                    sort_order,
                    created_at,
                    updated_at
                FROM saved_requests
                WHERE folder_id = ?
                ORDER BY sort_order, name COLLATE NOCASE, id
                """;

        List<SavedRequest> requests =
                new ArrayList<>();

        try (Connection connection =
                     database.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    folderId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    requests.add(
                            mapSavedRequest(resultSet)
                    );
                }
            }

            return requests;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to load saved requests for folder: "
                            + folderId,
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
                DELETE FROM saved_requests
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
                    "Unable to delete saved request: "
                            + id,
                    exception
            );
        }
    }

    private SavedRequest mapSavedRequest(
            ResultSet resultSet
    ) throws SQLException {

        return new SavedRequest(
                resultSet.getString("id"),
                resultSet.getString("collection_id"),
                resultSet.getString("folder_id"),
                resultSet.getString("name"),
                resultSet.getString("method"),
                resultSet.getString("url"),
                jsonSerializer.deserializeStringMap(
                        resultSet.getString("headers_json")
                ),
                jsonSerializer.deserializeStringMap(
                        resultSet.getString(
                                "query_parameters_json"
                        )
                ),
                resultSet.getString("body"),
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