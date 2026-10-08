package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SqliteCollectionImportPersistenceService
        implements CollectionImportPersistenceService {

    private final SqliteDatabase database;
    private final JacksonJsonSerializer jsonSerializer;

    public SqliteCollectionImportPersistenceService(
            SqliteDatabase database,
            JacksonJsonSerializer jsonSerializer
    ) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "SQLite database is required"
            );
        }

        if (jsonSerializer == null) {
            throw new IllegalArgumentException(
                    "JSON serializer is required"
            );
        }

        this.database = database;
        this.jsonSerializer = jsonSerializer;
    }

    @Override
    public void persist(
            ImportedCollection importedCollection
    ) {
        if (importedCollection == null) {
            throw new IllegalArgumentException(
                    "Imported collection is required"
            );
        }

        try (Connection connection =
                     database.openConnection()) {

            boolean previousAutoCommit =
                    connection.getAutoCommit();

            connection.setAutoCommit(false);

            try {
                Instant now =
                        Instant.now();

                String collectionId =
                        UUID.randomUUID().toString();

                insertCollection(
                        connection,
                        collectionId,
                        importedCollection.name(),
                        now
                );

                persistRequests(
                        connection,
                        importedCollection.requests(),
                        collectionId,
                        null,
                        now
                );

                persistFolders(
                        connection,
                        importedCollection.folders(),
                        collectionId,
                        null,
                        now
                );

                connection.commit();

            } catch (RuntimeException exception) {
                rollback(
                        connection,
                        exception
                );

                throw exception;

            } catch (SQLException exception) {
                rollback(
                        connection,
                        exception
                );

                throw new IllegalStateException(
                        "Unable to persist imported collection",
                        exception
                );

            } finally {
                connection.setAutoCommit(
                        previousAutoCommit
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to persist imported collection",
                    exception
            );
        }
    }

    private void persistFolders(
            Connection connection,
            List<ImportedFolder> folders,
            String collectionId,
            String parentFolderId,
            Instant timestamp
    ) throws SQLException {

        for (int index = 0;
             index < folders.size();
             index++) {

            ImportedFolder importedFolder =
                    folders.get(index);

            String folderId =
                    UUID.randomUUID().toString();

            insertFolder(
                    connection,
                    folderId,
                    collectionId,
                    parentFolderId,
                    importedFolder.name(),
                    index,
                    timestamp
            );

            persistRequests(
                    connection,
                    importedFolder.requests(),
                    collectionId,
                    folderId,
                    timestamp
            );

            persistFolders(
                    connection,
                    importedFolder.folders(),
                    collectionId,
                    folderId,
                    timestamp
            );
        }
    }

    private void persistRequests(
            Connection connection,
            List<ImportedRequest> requests,
            String collectionId,
            String folderId,
            Instant timestamp
    ) throws SQLException {

        for (int index = 0;
             index < requests.size();
             index++) {

            ImportedRequest importedRequest =
                    requests.get(index);

            String requestId =
                    UUID.randomUUID().toString();

            insertRequest(
                    connection,
                    requestId,
                    collectionId,
                    folderId,
                    importedRequest,
                    index,
                    timestamp
            );
        }
    }

    private void insertCollection(
            Connection connection,
            String id,
            String name,
            Instant timestamp
    ) throws SQLException {

        String sql =
                """
                INSERT INTO collections (
                    id,
                    name,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    id
            );

            statement.setString(
                    2,
                    name
            );

            statement.setString(
                    3,
                    timestamp.toString()
            );

            statement.setString(
                    4,
                    timestamp.toString()
            );

            statement.executeUpdate();
        }
    }

    private void insertFolder(
            Connection connection,
            String id,
            String collectionId,
            String parentFolderId,
            String name,
            int sortOrder,
            Instant timestamp
    ) throws SQLException {

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
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    id
            );

            statement.setString(
                    2,
                    collectionId
            );

            statement.setString(
                    3,
                    parentFolderId
            );

            statement.setString(
                    4,
                    name
            );

            statement.setInt(
                    5,
                    sortOrder
            );

            statement.setString(
                    6,
                    timestamp.toString()
            );

            statement.setString(
                    7,
                    timestamp.toString()
            );

            statement.executeUpdate();
        }
    }

    private void insertRequest(
            Connection connection,
            String id,
            String collectionId,
            String folderId,
            ImportedRequest request,
            int sortOrder,
            Instant timestamp
    ) throws SQLException {

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
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    id
            );

            statement.setString(
                    2,
                    collectionId
            );

            statement.setString(
                    3,
                    folderId
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
                            safeMap(request.headers())
                    )
            );

            statement.setString(
                    8,
                    jsonSerializer.serializeStringMap(
                            safeMap(
                                    request.queryParameters()
                            )
                    )
            );

            statement.setString(
                    9,
                    request.body()
            );

            statement.setInt(
                    10,
                    sortOrder
            );

            statement.setString(
                    11,
                    timestamp.toString()
            );

            statement.setString(
                    12,
                    timestamp.toString()
            );

            statement.executeUpdate();
        }
    }

    private Map<String, String> safeMap(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return new LinkedHashMap<>(values);
    }

    private void rollback(
            Connection connection,
            RuntimeException exception
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(
                    rollbackException
            );
        }
    }

    private void rollback(
            Connection connection,
            SQLException exception
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(
                    rollbackException
            );
        }
    }
}